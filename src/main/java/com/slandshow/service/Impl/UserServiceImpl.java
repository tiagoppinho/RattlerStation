package com.slandshow.service.Impl;

import com.slandshow.DAO.UserDAO;
import com.slandshow.DTO.UserDTO;
import com.slandshow.exceptions.ExceptionsInfo;
import com.slandshow.exceptions.InvalidUserRegistrationException;
import com.slandshow.models.Role;
import com.slandshow.models.User;
import com.slandshow.service.RoleService;
import com.slandshow.service.SecureService;
import com.slandshow.service.UserService;
import com.slandshow.utils.UtilsManager;
import org.apache.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import javax.transaction.Transactional;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private RoleService roleService;

    @Autowired
    private SecureService secureService;

    private static final Logger LOGGER = Logger.getLogger(UserServiceImpl.class);

    @Transactional
    public void add(User user) {
        userDAO.add(user);
    }

    @Transactional
    public void remove(User user) {
        userDAO.delete(user);
    }

    @Transactional
    public List<User> getUsers() {
        return userDAO.getAll();
    }

    @Transactional
    public UserDTO findAuthenticatedUserDTO() {
        ModelMapper modelMapper = new ModelMapper();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String login = authentication.getName();
        User user = userDAO.findUserByLogin(login);
        return modelMapper.map(user, UserDTO.class);
    }

    @Transactional
    public User findAuthenticatedUser() {
        String login = secureService.getAuthentication().getName();
        return userDAO.findUserByLogin(login);
    }

    @Transactional
    public User findUserByEmail(String email) {
        return userDAO.findUserByLogin(email);
    }

    @Transactional
    public void update(User user) {
        userDAO.update(user);
    }

    @Transactional
    public void updateProfile(UserDTO userDTO) throws ParseException {
        User user = findAuthenticatedUser();
        //Date birthDay = UtilsManager.parseToDate(userDTO.getBirthDay());

        // TODO: ADD OWN EXCEPRIONS LATER
        //if (!birthDay.before(new Date()))
        //    throw new ...;

        //if (findUserByEmail(userDTO.getLogin()) != null && !findUserByEmail(userDTO.getLogin()).getLogin().equals(user.getLogin()))
          //  throw new BusinessLogicException(ErrorCode.WRONG_LOGIN.getMessage());

        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setLogin(userDTO.getLogin());
        userDAO.updateProfile(user);
    }

    @Transactional
    public void registration(UserDTO userDTO) throws IOException, InvalidUserRegistrationException {
        if (userDTO == null) {
            LOGGER.info(ExceptionsInfo.REGISTERED_USER_IS_NULL);
            throw new InvalidUserRegistrationException(ExceptionsInfo.REGISTERED_USER_IS_NULL);
        }

       if (findUserByEmail(userDTO.getLogin()) != null) {
            LOGGER.info(ExceptionsInfo.USER_ALREADY_EXIST);
            throw new InvalidUserRegistrationException(ExceptionsInfo.USER_ALREADY_EXIST);
        }

        Role role = roleService.getRole();
        Set<Role> roleSet = new HashSet<Role>();
        roleSet.add(role); // User role
        User user = new User();
        user.setLogin(userDTO.getLogin());
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setPassword(UtilsManager.encodePassword(userDTO.getPassword()));
        user.setRoles(roleSet);
        add(user);
    }

    @Transactional
    public void addUserRole(Long userId, Long roleId) {
        userDAO.addUserRole(userId, roleId);
    }

    @Transactional
    public void deleteUserRole(Long userId) {
        userDAO.deleteUserRole(userId);
    }
}
