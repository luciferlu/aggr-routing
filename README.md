aggr-routing
============

This is a Akka router to delivery message with three patterns:
* delivery message to specified number of routees at same time and forward first complete result to sender
* split messages, delivery each message to a routee and forward collected result to sender
* split messages, delivery each message to specified number of routees, first compete win, and than collect all result forward to sender

Sample
------

