package com.slandshow.service.Impl;

import com.slandshow.DAO.TicketDAO;
import com.slandshow.DTO.*;
import com.slandshow.exceptions.BookingTicketException;
import com.slandshow.exceptions.BusinessLogicException;
import com.slandshow.exceptions.ExceptionsInfo;
import com.slandshow.models.*;
import com.slandshow.service.*;
import com.slandshow.utils.UtilsManager;
import org.apache.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TicketServiceImpl implements TicketService {

    private static final Logger LOGGER = Logger.getLogger(TicketServiceImpl.class);

    @Autowired
    private TicketDAO ticketDAO;

    @Autowired
    private SeatService seatService;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private DistanceAndPriceUtilsService distanceService;

    @Autowired
    private SecureService secureService;

    @Autowired
    private UserService userService;

    @Autowired
    private GraphService graphService;

    @Override
    @Transactional
    public void add(Ticket ticket) {
        ticketDAO.add(ticket);
    }

    /*
     * Booking ticket
     *
     * Before we buy ticket, we must check:
     * 1) Correct schedule
     * 2) Unique person
     * 3) Free seats
     * 4) Before start more than 10 minutes
     *
     */

    @Override
    @Transactional
    public Ticket add(TicketDTO ticketDTO, User user) throws BookingTicketException, ParseException {
        Schedule schedule = scheduleService.getById(ticketDTO.getScheduleId());

        if (schedule == null || user == null) {
            LOGGER.info(ExceptionsInfo.USER_OR_SCHEDULE_NOT_EXISTS);
            throw new BookingTicketException(ExceptionsInfo.USER_OR_SCHEDULE_NOT_EXISTS);
        }

        if (!checkUserUntilBooking(user, schedule)) {
            LOGGER.info(ExceptionsInfo.SAME_USER_TRY_TO_BOOK_SEAT);
            throw new BookingTicketException(ExceptionsInfo.SAME_USER_TRY_TO_BOOK_SEAT);
        }

        if (!checkScheduleForAvailability(schedule)) {
            LOGGER.info(ExceptionsInfo.SCHEDULE_NOT_AVAILABLE_NOW);
            throw new BookingTicketException(ExceptionsInfo.SCHEDULE_NOT_AVAILABLE_NOW);
        }

        LOGGER.info("HERE ->" + schedule.getTrain());

        Train train = schedule.getTrain();
        LOGGER.info("TICKET SERVICE: TRAIN IS " + train +
                " CARRIAGE IS " + ticketDTO.getSeatDTO().getCarriage() +
                " SEAT IS " + ticketDTO.getSeatDTO().getSeat()
        );

        Seat seat = seatService.getByTrainAndCarriageAndSeat(
                train,
                ticketDTO.getSeatDTO().getCarriage(),
                ticketDTO.getSeatDTO().getSeat()
        );

        LOGGER.info("SEAT DETECTED: " + seat);

        if (seat == null || !checkSeatUntilBooking(seat, schedule)) {
            LOGGER.info(ExceptionsInfo.USER_ALREADY_ON_THIS_SEAT);
            throw new BookingTicketException(ExceptionsInfo.USER_ALREADY_ON_THIS_SEAT);
        }

        Ticket ticket = new Ticket();
        ticket.setSchedule(schedule);
        ticket.setSeat(seat);
        ticket.setUser(user);

        // Calsulate trip price
        ticket.setPrice(ticketDTO.getPrice());

        // Add ticket in DB
        add(ticket);

        LOGGER.info("TICKED RESERVED!");

        return ticket;
    }

    @Override
    @Transactional
    public void delete(Ticket ticket) {
        ticketDAO.delete(ticket);
    }

    @Override
    @Transactional
    public void update(Ticket ticket) {
        ticketDAO.update(ticket);
    }

    @Override
    @Transactional
    public List<Ticket> getAll() {
        return ticketDAO.getAll();
    }

    @Override
    @Transactional
    public Ticket getById(Long id) {
        return (Ticket) ticketDAO.getById(id);
    }

    /* Return reserved seats */
    @Transactional
    @Override
    public List<Seat> getBookingSeatsBySchedule(Schedule schedule) {
        List<Ticket> tickets = ticketDAO.getBySchedule(schedule);
        List<Seat> bookingSeats = new ArrayList<>();
        for (Ticket ticket :
                tickets) {
            bookingSeats.add(ticket.getSeat());
        }
        return bookingSeats;
    }

    /* Check if user already have ticket */
    @Override
    @Transactional
    public boolean checkUserUntilBooking(User user, Schedule schedule) {
        return ticketDAO.findSameUserOnTrain(user, schedule).isEmpty();
    }

    /* Check if we have correct schedule */
    @Override
    @Transactional
    public boolean checkSeatUntilBooking(Seat seat, Schedule schedule) {
        return ticketDAO.findTicketByScheduleAndSeat(schedule, seat) == null;
    }

    /* Check if we can buy ticket in time & data case */
    @Override
    @Transactional
    public boolean checkScheduleForAvailability(Schedule schedule) throws BookingTicketException {
        Date date = schedule.getDateDeparture();
        return UtilsManager.checkForCurrentDayForBookingTicket(date);
    }

    @Override
    @Transactional
    public List<Ticket> getBySchedules(Schedule schedule) {
        return ticketDAO.getBySchedule(schedule);
    }

    @Override
    @Transactional
    public List<TicketInfoDTO> getByScheduleId(Long id) {
        ModelMapper modelMapper = new ModelMapper();
        Schedule schedule = scheduleService.getById(id);
        List<Ticket> tickets = getBySchedules(schedule);
        return tickets.stream()
                .map(x -> modelMapper.map(x, TicketInfoDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<TicketInfoDTO> getAuthenticatedUserTicket() {
        ModelMapper modelMapper = new ModelMapper();
        String userName = secureService.getAuthentication().getName();
        User user = userService.findUserByEmail(userName);
        List<Ticket> tickets = ticketDAO.getByUser(user);
        return tickets.stream()
                .map(x -> modelMapper.map(x, TicketInfoDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<Ticket> getByDate(Date date) {
        return ticketDAO.getByDate(date);
    }

    @Override
    @Transactional
    public List<Ticket> getByDates(Date dateFrom, Date dateTo) {
        return ticketDAO.getByDates(dateFrom, dateTo);
    }

    @Override
    public List<List<SeatDTO>> getSeatsMatrix(int row, int col) {
        return seatService.createSeatsMatrix(row, col);
    }

    @Override
    public List<TicketDTO> getPuzzledTickets(List<Schedule> puzzledSchedulers, int seat, int carriage) throws ParseException, BookingTicketException {

        // Create puzzled tickets for puzzled schedulers
        List<TicketDTO> ticketDTOS = new ArrayList<>();

        for (int i = 0; i < puzzledSchedulers.size(); i++) {
            if (i == 0 && !timeIsUnder10Minutes(UtilsManager.getTodayDateTime(), puzzledSchedulers.get(i).getDateDeparture())) {
                LOGGER.info(ExceptionsInfo.TIME_IS_MORE_THAT_10_MINUTES);
                throw new BookingTicketException(ExceptionsInfo.TIME_IS_MORE_THAT_10_MINUTES);
            }

            TicketDTO ticketDTO = new TicketDTO();
            ticketDTO.setScheduleId(puzzledSchedulers.get(i).getId());

            SeatDTO seatDTO = new SeatDTO();
            seatDTO.setSeat(seat);
            seatDTO.setCarriage(carriage);
            ticketDTO.setSeatDTO(seatDTO);
            ticketDTO.setPrice(
                    Math.abs(
                        distanceService.calculateDirectTripPrice(
                                scheduleService.getById(
                                        puzzledSchedulers.get(i).getId()
                                )
                        )
                    )
            );

            ticketDTOS.add(ticketDTO);
        }

        return ticketDTOS;
    }

    @Override
    public BookingTicketInfoDTO getBookingStatusInfo(int seat, int carriage, UserDTO userDTO) {

        // Booking result information DTO creation
        BookingTicketInfoDTO ticketInfoDTO = new BookingTicketInfoDTO();
        ticketInfoDTO.setSeatNumber(seat);
        ticketInfoDTO.setCarriageNumber(carriage);

        ticketInfoDTO.setUser(
                userDTO.getFirstName() + " "
                        + userDTO.getLastName() + " ("
                        + userDTO.getLogin() + ")"
        );

        return ticketInfoDTO;
    }

    @Override
    public BookingTicketInfoDTO getBookingStatusInfo(List<TicketDTO> ticketDTOS, UserDTO userDTO) {

        BookingTicketInfoDTO ticketInfoDTO = new BookingTicketInfoDTO();
        ticketInfoDTO.setSeatNumber(
                ticketDTOS.get(0).getSeatDTO().getSeat()
        );

        ticketInfoDTO.setCarriageNumber(
                ticketDTOS.get(0).getSeatDTO().getCarriage()
        );

        ticketInfoDTO.setStationDepartureName(
                scheduleService.getById(
                        ticketDTOS.get(0).getScheduleId()
                ).getStationDeparture().getName()
        );

        ticketInfoDTO.setStationArrivalName(
                scheduleService.getById(
                        ticketDTOS.get(ticketDTOS.size() - 1).getScheduleId()
                ).getStationArrival().getName()
        );

        ticketInfoDTO.setDateDeparture(
                scheduleService.getById(
                        ticketDTOS.get(0).getScheduleId()
                ).getDateDeparture()
        );

        ticketInfoDTO.setDateArrival(
                scheduleService.getById(
                       ticketDTOS.get(ticketDTOS.size() - 1).getScheduleId()
                ).getDateArrival()
        );

        ticketInfoDTO.setUser(
                userDTO.getFirstName() + " "
                        + userDTO.getLastName() + " ("
                        + userDTO.getLogin() + ")"
        );

        Integer globalPrice = 0;

        for (TicketDTO priceIterator: ticketDTOS) {
            globalPrice += priceIterator.getPrice();
        }

        ticketInfoDTO.setPrice(globalPrice);

        return ticketInfoDTO;
    }

    @Override
    public Map<ScheduleDTO, List<Schedule>> createPuzzledTickets(String start, String end, String dateDeparture, String dateArrival) throws ParseException {
        graphService.buildGraph();
        return graphService.puzzleSchedules(start, end, dateDeparture, dateArrival);
    }

    @Override
    public List<ScheduleDTO> parsedListFromMap(Map<ScheduleDTO, List<Schedule>> filtered) {
        return graphService.parsedListFromMap(filtered);
    }

    @Override
    public boolean timeIsUnder10Minutes(Date selected, Date departure) {
        Date foresee = null;
        try {
            foresee = UtilsManager.addNMinutes(
                    UtilsManager.parseToDateTime(
                            UtilsManager.convertDateToString(selected)
                    ),
                    10
            );

            System.out.println(UtilsManager.convertDateToString(foresee));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return foresee.compareTo(departure) == 0 || foresee.compareTo(departure) < 0;
    }
}
