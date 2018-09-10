<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%--
  Created by IntelliJ IDEA.
  User: slandshow
  Date: 09.09.18
  Time: 19:35
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Title</title>
</head>
<body>
<div class="container">

    <!-- Add HTML table -->
    <table class="table table-striped">

        <tr>
            <th>Station departure</th>
            <th>Station arrival</th>
            <th>Date departure</th>
        </tr>

        <!-- Loop over and print stations  -->
        <c:forEach var="tmpSchedule" items="${schedules}">
            <tr>
                <td>${tmpSchedule.stationDeparture}</td>
                <td>${tmpSchedule.stationArrival}</td>
                <td>${tmpSchedule.dateDeparture}/></td>
                <td><a href="/bookingTicket?id=${tmpSchedule.id}">Buy</a> </td>
            </tr>
        </c:forEach>

    </table>

</div>
</body>
</html>