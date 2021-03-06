package com.slandshow.service.Impl;

import com.slandshow.DAO.ScheduleDAO;
import com.slandshow.DAO.StateDAO;
import com.slandshow.DTO.ScheduleDTO;
import com.slandshow.DTO.SeatDTO;
import com.slandshow.DTO.SeatsDTO;
import com.slandshow.DTO.TrainDTO;
import com.slandshow.exceptions.ExceptionsInfo;
import com.slandshow.exceptions.ScheduleCreationException;
import com.slandshow.models.*;
import com.slandshow.service.*;
import com.slandshow.utils.DistanceManager;
import com.slandshow.utils.UtilsManager;
import org.apache.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
public class ScheduleServiceImpl implements ScheduleService {

    private static final Logger LOGGER = Logger.getLogger(ScheduleServiceImpl.class);

    @Autowired
    private ScheduleDAO scheduleDAO;

    @Autowired
    private StationService stationService;

    @Autowired
    private TrainService trainService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private StateDAO stateDAO;

    @Autowired
    private DistanceAndPriceUtilsService distanceService;

    @Autowired
    private MessageQueueService messageQueueService;

    @Autowired
    private GraphService graphService;


    /**
     *
     * create schedule (check intersection of times and correctness of times)
     *
     * @param scheduleDTO
     */

    @Transactional
    public void add(ScheduleDTO scheduleDTO) throws ScheduleCreationException, ParseException {
        Train train = trainService.getByName(scheduleDTO.getTrainName());
        Station stationArrival = stationService.getByName(scheduleDTO.getStationArrivalName().intern());
        Station stationDeparture = stationService.getByName(scheduleDTO.getStationDepartureName().intern());

        if (stationArrival == null || stationDeparture == null || train == null) {
            LOGGER.info(ExceptionsInfo.TRAINS_STATIONS_ARE_NULL);
            throw new ScheduleCreationException(ExceptionsInfo.TRAINS_STATIONS_ARE_NULL);
        }

        Date dateDeparture = UtilsManager.parseToDateTime(scheduleDTO.getDateDeparture());

        Date dateArrival;
        if (scheduleDTO.getDateArrival().isEmpty())
            dateArrival = distanceService.calculateDateArrival(dateDeparture, stationDeparture, stationArrival);
        else dateArrival = UtilsManager.parseToDateTime(scheduleDTO.getDateArrival());

        Schedule schedule = new Schedule();
        schedule.setStationDeparture(stationDeparture);
        schedule.setStationArrival(stationArrival);
        schedule.setDateDeparture(dateDeparture);
        schedule.setDateArrival(dateArrival);
        schedule.setTrain(train);


        if (stationArrival.equals(stationDeparture)) {
            LOGGER.info(ExceptionsInfo.STATIONS_ARE_THE_SAME);
            throw new ScheduleCreationException(ExceptionsInfo.STATIONS_ARE_THE_SAME);
        }

        if (!dateDeparture.before(dateArrival)) {
            LOGGER.info(ExceptionsInfo.DATE_DEPARTURE_GREATER_THAN_ARRIVAL);
            throw new ScheduleCreationException(ExceptionsInfo.DATE_DEPARTURE_GREATER_THAN_ARRIVAL);
        }

        if (!getByDateAndTrainToCheckIntersection(schedule).isEmpty()) {
            LOGGER.info(ExceptionsInfo.TRAIN_INTERSECTION);
            throw new ScheduleCreationException(ExceptionsInfo.TRAIN_INTERSECTION);
        }

        // TODO: THIS FUNCTIONAL IS NOT AVAILABLE FOR SECOND PROJECT MEET-UP
        /*
        if (UtilsManager.checkCurrentDay(dateDeparture)) {
            LOGGER.info(ExceptionsInfo.SCHEDULE_CURRENT_DAY_CREATION);
            throw new ScheduleCreationException(ExceptionsInfo.SCHEDULE_CURRENT_DAY_CREATION);
        }
        */

        State state = stateDAO.getByType("VALID");
        schedule.getStationDeparture().setState(state);
        schedule.getStationArrival().setState(state);
        schedule.getTrain().setState(state);

        scheduleDAO.add(schedule);
        messageQueueService.getMessagesInstance().add("create id=" + schedule.getId());

        LOGGER.info("SCHEDULE WAS CREATED!");
    }

    @Transactional
    public List<ScheduleDTO> creatingSchedulers(String start, String end, String dateDeparture, String dateArrival, String train) throws ParseException, ScheduleCreationException {
        List<ScheduleDTO> createdSchedulers = new ArrayList<>();
        String tmpDate = null;

        // If graph not built yet
        graphService.buildGraph();

        // Get path of stations (A -> ... -> ... -> B)
        String[] path = graphService.parsePath(
                graphService.searchEdges(start.intern(), end.intern())
        );

        // If there is no way from A to B
        if (path.length == 0) {
            // TODO: ADD NEW CUSTOM EXCEPTION
        }

        if (stationService.getStationByName(start) == null) {

        }

        if (stationService.getStationByName(end) == null) {

        }

        for (int i = 0; i < path.length; i++) {
            ScheduleDTO schedule = new ScheduleDTO();
            schedule.setStationDepartureName(path[i].split("->")[0]);
            schedule.setStationArrivalName(path[i].split("->")[1]);
            schedule.setTrainName(train);

            // Check, if station A or station B is UNUSED
            if (stationService.getByName(schedule.getStationDepartureName()) == null) {

            }


            if (dateArrival == null || dateArrival.equals("")) {

                if (i == 0) {
                    schedule.setDateDeparture(dateDeparture);

                    Date calculatedDate = distanceService.calculateDateArrival(
                            UtilsManager.parseToDateTime(dateDeparture),
                            stationService.getByName(schedule.getStationDepartureName()),
                            stationService.getByName(schedule.getStationArrivalName())
                    );

                    tmpDate = UtilsManager.convertDateToString(calculatedDate);
                    schedule.setDateArrival(tmpDate);
                } else {

                    String newDateDeparture = UtilsManager.convertDateToString(
                            UtilsManager.addNMinutes(
                                    UtilsManager.parseToDateTime(tmpDate),
                                    DistanceManager.STATION_STOP_TIMING
                            )
                    );

                    schedule.setDateDeparture(newDateDeparture);

                    schedule.setDateArrival(
                            UtilsManager.convertDateToString(
                                    distanceService.calculateDateArrival(
                                            UtilsManager.parseToDateTime(schedule.getDateDeparture()),
                                            stationService.getByName(schedule.getStationDepartureName()),
                                            stationService.getByName(schedule.getStationArrivalName())
                                    )
                            )
                    );

                    tmpDate = schedule.getDateArrival();
                }

                schedule.setPrice(Math.abs(distanceService.calculateDirectTripPrice(schedule)));

                createdSchedulers.add(schedule);
            }
        }

        for (int i = 0; i < createdSchedulers.size(); i++) {
            LOGGER.info(
                             "SCHEDULE N " + i
                            + " IS " + createdSchedulers.get(i).getStationDepartureName() + " - > "
                            + createdSchedulers.get(i).getStationArrivalName()
                            + " " + createdSchedulers.get(i).getDateDeparture() + " -> "
                            + " " + createdSchedulers.get(i).getDateArrival()
            );
            add(createdSchedulers.get(i));
        }

        return createdSchedulers;
    }


    @Transactional
    public void delete(Long id) throws ScheduleCreationException, ParseException, TimeoutException {
        Schedule schedule = getById(id);

        if (!ticketService.getBySchedules(schedule).isEmpty()) {
            LOGGER.debug(ExceptionsInfo.DELETING_SCHEDULE_PROBLEM);
            throw new ScheduleCreationException(ExceptionsInfo.DELETING_SCHEDULE_PROBLEM);
        }

        scheduleDAO.delete(schedule);
        messageQueueService.getMessagesInstance().add("delete id=" + schedule.getId());
    }

    @Transactional
    public void update(ScheduleDTO scheduleDTO) throws ParseException, IOException, TimeoutException {
        Schedule schedule = getById(scheduleDTO.getId());
        Schedule scheduleOld = schedule;
        if (!ticketService.getBySchedules(schedule).isEmpty())
            throw new IOException();

        Train train = trainService.getByName(scheduleDTO.getTrainName());
        Station stationDeparture = stationService.getByName(scheduleDTO.getStationDepartureName());
        Station stationArrival = stationService.getByName(scheduleDTO.getStationArrivalName());

        // TODO: ADD CUSTOM EXCEPTIONS
        if (train == null || stationDeparture == null || stationArrival == null)
            throw new RuntimeException();

        Date dateDeparture = UtilsManager.parseToDateTime(scheduleDTO.getDateDeparture());
        Date dateArrival = UtilsManager.parseToDateTime(scheduleDTO.getDateArrival());

        train.setName(scheduleDTO.getTrainName());
        stationDeparture.setName(scheduleDTO.getStationDepartureName());
        stationArrival.setName(scheduleDTO.getStationArrivalName());
        schedule.setTrain(train);
        schedule.setStationDeparture(stationDeparture);
        schedule.setStationArrival(stationArrival);
        schedule.setDateDeparture(dateDeparture);
        schedule.setDateArrival(dateArrival);

        if (stationDeparture.equals(stationArrival))
            throw new RuntimeException();

        if (!dateDeparture.before(dateArrival))
            throw new RuntimeException();

        if (getByDateAndTrainToCheckIntersection(schedule).size() > 1)
            throw new RuntimeException();

        if (UtilsManager.checkCurrentDay(dateDeparture))
            throw new RuntimeException();

        scheduleDAO.update(schedule);
        messageQueueService.getMessagesInstance().add("update id=" + schedule.getId());
    }

    @Transactional
    public List<ScheduleDTO> getAll() {
        ModelMapper modelMapper = new ModelMapper();

        List<Schedule> schedules = scheduleDAO.getAll();
        return schedules.stream()
                .map(x -> modelMapper.map(x, ScheduleDTO.class))
                .collect(Collectors.toList());
    }


    @Transactional
    public List<ScheduleDTO> getAllForToday() throws ParseException {
        ModelMapper modelMapper = new ModelMapper();
        List<Schedule> schedules = scheduleDAO.getForToday();

        return schedules.stream()
                .map(x -> modelMapper.map(x, ScheduleDTO.class))
                .collect(Collectors.toList());
    }

    @Transactional
    public Schedule getById(Long id) {
        Schedule schedule = (Schedule) scheduleDAO.getById(id);
        LOGGER.info("SELECTED BY ID = " + id + " SCHEDULE IS " + schedule);
        return schedule;
    }

    @Transactional
    public List<Schedule> getByDate(Date dateDeparture) {
        return scheduleDAO.getByDate(dateDeparture);
    }

    @Transactional
    public List<Schedule> getByDates(Date dateDeparture, Date dateArrival) throws ParseException {
        dateArrival = UtilsManager.getNextDay(dateArrival);
        if (dateArrival.before(dateDeparture)) {
            LOGGER.info("INCORRECT DATA, CANT RETURN SCHEDULE BY DATES!");
        }

        return scheduleDAO.getByDates(dateDeparture, dateArrival);
    }

    @Transactional
    public List<Schedule> getByStationsAndDate(Schedule schedule) {
        return scheduleDAO.getByStationsAndDate(schedule);
    }

    @Transactional
    public List<Schedule> getByDateAndTrainToCheckIntersection(Schedule schedule) {
        return scheduleDAO.getByDateAndTrainToCheckIntersection(schedule);
    }

    @Transactional
    public List<Schedule> getByTrain(Train train) {
        return scheduleDAO.getByTrain(train);
    }

    @Transactional
    public List<Schedule> getByTrainAndDate(Schedule schedule) {
        return scheduleDAO.getByTrainAndDate(schedule);
    }

    @Transactional
    public List<Schedule> getByStationsAndDates(Schedule schedule) {
        return scheduleDAO.getByStationsAndDates(schedule);
    }

    @Transactional
    public List<Schedule> getByStationArrivalAndDate(Schedule schedule) {
        return scheduleDAO.getByStationArrivalAndDate(schedule);
    }

    /**
     * get all schedules for direct trip by all parameters
     *
     * @param scheduleDTO
     * @return
     * @throws ParseException
     */
    @Override
    @Transactional
    public List<ScheduleDTO> getDirectSchedulesFromDTOByStationsAndDatesAndTrain(ScheduleDTO scheduleDTO) throws
            ParseException {
        List<Schedule> schedules;
        Schedule schedule = new Schedule();
        Train train = trainService.getByName(scheduleDTO.getTrainName());
        Station stationDeparture = stationService.getByName(scheduleDTO.getStationDepartureName());
        Station stationArrival = stationService.getByName(scheduleDTO.getStationArrivalName());
        Date dateDeparture = UtilsManager.parseToDate(scheduleDTO.getDateDeparture());
        Date dateArrival = UtilsManager.parseToDate(scheduleDTO.getDateArrival());
        if (train == null || stationArrival == null || stationDeparture == null)
            throw new RuntimeException();

        if (scheduleDTO.getDateArrival().equals(scheduleDTO.getDateDeparture()))
            dateArrival = UtilsManager.getNextDay(scheduleDTO.getDateDeparture());

        if (dateArrival.before(dateDeparture))
            throw new RuntimeException();

        schedule.setTrain(train);
        schedule.setDateArrival(dateArrival);
        schedule.setDateDeparture(dateDeparture);
        schedule.setStationDeparture(stationDeparture);
        schedule.setStationArrival(stationArrival);
        schedules = scheduleDAO.getByStationsAndDatesAndTrains(schedule);

        return mapping(schedules);
    }

    @Override
    public List<ScheduleDTO> getInfoByStation(TrainDTO trainDTO) {
        return null;
    }

    /**
     * get schedules by stations and date/dates
     *
     * @param scheduleDTO
     * @return
     * @throws ParseException
     */
    @Override
    @Transactional
    public List<ScheduleDTO> getDirectSchedulesFromDTOByStations(ScheduleDTO scheduleDTO) throws ParseException {
        Station stationDepartureForDirectSchedule = stationService.getByName(scheduleDTO.getStationDepartureName());
        Station stationArrivalForDirectSchedule = stationService.getByName(scheduleDTO.getStationArrivalName());
        List<Schedule> schedules;

        if (stationArrivalForDirectSchedule == null || stationDepartureForDirectSchedule == null)
            throw new RuntimeException();

        Schedule schedule = new Schedule();
        schedule.setStationDeparture(stationDepartureForDirectSchedule);
        schedule.setStationArrival(stationArrivalForDirectSchedule);
        Date dateDeparture = UtilsManager.parseToDate(scheduleDTO.getDateDeparture());
        schedule.setDateDeparture(dateDeparture);
        if (!scheduleDTO.getDateArrival().isEmpty()) {
            Date dateArrival = UtilsManager.parseToDate(scheduleDTO.getDateArrival());

            if (scheduleDTO.getDateArrival().equals(scheduleDTO.getDateDeparture()))
                dateArrival = UtilsManager.getNextDay(scheduleDTO.getDateArrival());

            if (dateArrival.before(dateDeparture))
                throw new RuntimeException();

            schedule.setDateArrival(dateArrival);
            schedules = scheduleDAO.getByStationsAndDates(schedule);
        } else schedules = scheduleDAO.getByStationsAndDate(schedule);
        LOGGER.info("FOUND SCHEDULES BY STATIONS AND DATE");


        return mapping(schedules);
    }

    /**
     * get direct schedules by train and date/dates
     *
     * @param scheduleDTO
     * @return
     * @throws ParseException
     */
    @Override
    @Transactional
    public List<ScheduleDTO> getDirectSchedulesFromDTOByTrain(ScheduleDTO scheduleDTO) throws ParseException {
        Train train = trainService.getByName(scheduleDTO.getTrainName());
        List<Schedule> schedules;
        if (train == null)
            throw new RuntimeException();

        Schedule schedule = new Schedule();
        Date dateDeparture = UtilsManager.parseToDate(scheduleDTO.getDateDeparture());
        schedule.setDateDeparture(dateDeparture);
        schedule.setTrain(train);
        if (!scheduleDTO.getDateArrival().isEmpty()) {
            Date dateArrival = UtilsManager.parseToDate(scheduleDTO.getDateArrival());
            if (scheduleDTO.getDateArrival().equals(scheduleDTO.getDateDeparture()))
                dateArrival = UtilsManager.getNextDay(scheduleDTO.getDateArrival());

            if (dateArrival.before(dateDeparture))
                throw new RuntimeException();

            schedule.setDateArrival(dateArrival);
            schedules = scheduleDAO.getByTrainAndDates(schedule);
        } else schedules = scheduleDAO.getByTrainAndDate(schedule);
        LOGGER.info("FOUND SCHEDULES BY TRAIN AND DATE");

        return mapping(schedules);
    }

    /**
     * get direct schedules by date/dates
     *
     * @param scheduleDTO
     * @return
     * @throws ParseException
     */

    @Transactional
    public List<ScheduleDTO> getDirectSchedulesFromDTOByDates(ScheduleDTO scheduleDTO) throws ParseException {
        Date dateDeparture = UtilsManager.parseToDate(scheduleDTO.getDateDeparture());
        List<Schedule> schedules;
        if (!scheduleDTO.getDateArrival().isEmpty()) {
            Date dateArrival = UtilsManager.parseToDate(scheduleDTO.getDateArrival());

            if (scheduleDTO.getDateArrival().equals(scheduleDTO.getDateDeparture()))
                dateArrival = UtilsManager.getNextDay(scheduleDTO.getDateArrival());

            if (dateArrival.before(dateDeparture))
                throw new RuntimeException();

            schedules = scheduleDAO.getByDates(dateDeparture, dateArrival);
        } else schedules = scheduleDAO.getByDate(dateDeparture);
        return mapping(schedules);
    }

    @Transactional
    public List<Schedule> getAllSchedules() {
        return scheduleDAO.getAll();
    }


    @Transactional
    public SeatsDTO getSeats(Long id) throws ScheduleCreationException {
        ModelMapper modelMapper = new ModelMapper();
        Schedule schedule = getById(id);

        // TODO: ADD CUSTOM EXCEPTIONS
        if (schedule == null)
            throw new ScheduleCreationException(ExceptionsInfo.SCHEDULE_IS_NULL);

        Train train = schedule.getTrain();
        Set<Seat> seats = train.getSeats();
        List<Seat> bookingSeats = ticketService.getBookingSeatsBySchedule(schedule);
        Integer cntCarriage = Collections.max(seats.stream().map(x -> x.getCarriage()).collect(Collectors.toList()));
        List<SeatDTO> seatDTOList = bookingSeats.stream().map(x -> modelMapper.map(x, SeatDTO.class)).collect(Collectors.toList());
        SeatsDTO seatsDTO = new SeatsDTO();
        seatsDTO.setBookingSeats(seatDTOList);
        seatsDTO.setCntCarriages(cntCarriage);
        return seatsDTO;
    }

    @Transactional
    public List<Schedule> getByStationArrivalAndDates(Station station, Date dateFrom, Date dateTo) {
        return scheduleDAO.getByStationArrivalAndDates(station, dateFrom, dateTo);
    }

    @Transactional
    public ScheduleDTO getByIdScheduleDTO(Long id) {
        ModelMapper modelMapper = new ModelMapper();
        ScheduleDTO schedule = modelMapper.map(getById(id), ScheduleDTO.class);

        try {
            schedule.setPrice(
                    Math.abs(
                            distanceService.calculateDirectTripPrice(schedule)
                    )
            );
        } catch (ParseException e) {
            e.printStackTrace();
            schedule.setPrice(0);
        }

        return schedule;
    }

    public List<ScheduleDTO> mapping(List<Schedule> schedules) {
        ModelMapper modelMapper = new ModelMapper();

        return schedules.stream()
                .map(x -> modelMapper.map(x, ScheduleDTO.class))
                .map(x -> {
                    Integer price = 0;
                    try {
                        price = distanceService.calculateDirectTripPrice(x);
                    } catch (ParseException e) {
                        LOGGER.error(e.getMessage());
                        e.printStackTrace();
                    }
                    x.setPrice(price);
                    return x;
                })
                .collect(Collectors.toList());
    }

    public Schedule mapping(ScheduleDTO scheduleDTO) throws ParseException {
        Schedule mapped =  new Schedule();

        mapped.setStationDeparture(
                        stationService.getStationByName(
                                scheduleDTO.getStationDepartureName()
                        )
        );

        mapped.setStationArrival(
                        stationService.getStationByName(
                                scheduleDTO.getStationArrivalName()
                        )
        );

        mapped.setDateDeparture(
                UtilsManager.parseToDateTime(scheduleDTO.getDateDeparture())
        );

        if (!(scheduleDTO.getDateArrival().equals(""))) {
            mapped.setDateArrival(
                    UtilsManager.parseToDateTime(scheduleDTO.getDateArrival())
            );
        } else mapped.setDateArrival(null);

        LOGGER.info(
                "LOAD MAPPED DTO DATA IN SCHEDULE SERVICE:\n Stations: "
                        + mapped.getStationDeparture().getName()
                        + " -> " + mapped.getStationArrival().getName()
                        + " in time range " + mapped.getDateDeparture()
                        + " -> " + mapped.getDateArrival()
        );

        return mapped;
    }

    @Override
    public void produceMessagesToServer(String message) {
        try {
            messageQueueService.produceMessagesInOneTransaction();
        } catch (IOException e) {
         LOGGER.error("CANNOT PRODUCE MESSAGE TO RATTLER STATION BOARD APP [" + message + "]");
         e.printStackTrace();
        } catch (TimeoutException e) {
         LOGGER.error("TIME OF SENDING MESSAGE GONE...");
         e.printStackTrace();
        }
    }
}
