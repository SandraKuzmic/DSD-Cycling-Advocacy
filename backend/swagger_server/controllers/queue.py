import pika
import json
import uuid
import logging
from threading import Thread
from swagger_server.controllers import constants as const
from swagger_server.controllers import road_analysis

logging.basicConfig(filename='log.log', level=logging.DEBUG)

"""
This module contains two main features: job publishing and job consumption.

--- JOB PUBLISHING ---
To publish a new job on the queue: 
    import queue
    queue.Job(const.JOB_TYPE, data).enqueue_job(const.QUEUE_NAME)
This will send a message to a queue, containing data and the job type to be performed.

--- JOB CONSUMPTION ---
A listener has to be listening to a queue to consume jobs inside the queue.
To instantiate a Listener: 
    import queue
    queue.new_listener(const.QUEUE_NAME).
"""


# a job is just:
# # a job type (i.e. the callback function to be called by the listener)
# # data to be passed to the function (i.e. trip data) (or, better, tip_id, then retrieve trip from mongo)
class Job:

    def __init__(self, job_type, job_data):
        self.job_id = uuid.uuid4().__str__()
        self.job_type = job_type
        self.job_data = job_data

    # serialize the info and publish the message on the provided queue
    # TODO exceptions in case publishing fails
    def enqueue_job(self, queue):
        job = json.dumps({'id': self.job_id, 'type': self.job_type, 'data': self.job_data})
        connection = pika.BlockingConnection(pika.ConnectionParameters(host=const.RABBITMQ_HOST))
        channel = connection.channel()
        channel.queue_declare(queue=queue)
        channel.basic_publish(exchange='', routing_key=queue, body=job)
        logging.info('New job published on queue: %s' % queue)
        connection.close()
        return


# job getters are fundamental because jobs are json messages, and they need to be parsed

def get_job_type(serialized_job):
    return json.loads(serialized_job)['type']


def get_job_data(serialized_job):
    return json.loads(serialized_job)['data']


def get_job_id(serialized_job):
    return json.loads(serialized_job)['id']


# create a new listener inside a thread, listening to provided queue
def new_listener(queue_name):
    # instantiate a listener for trip analysis queue on a new thread and test it
    new_thread = Thread(target=listen, args=(queue_name,))
    new_thread.start()
    logging.info('Started a new thread with listener on queue: %s' % queue_name)
    return new_thread


# callback function for TRIP_ANALYSIS_JOB
def execute_trip_analysis_job(trip_id):
    logging.info('Starting trip analysis of trip:: %s' % trip_id)
    road_analysis.run_trip_analysis(trip_id)
    logging.info('Finished trip analysis of trip: %s' % trip_id)
    return


def execute_map_update_job(trip_id):
    logging.info('Starting map update with trip: %s' % trip_id)
    road_analysis.update_map(trip_id)
    logging.info(('Finished map update with trip: %s' % trip_id))
    return


# TODO exception to handle disconnection
def listen(queue_name):
    connection = pika.BlockingConnection(pika.ConnectionParameters(host=const.RABBITMQ_HOST))
    channel = connection.channel()
    channel.queue_declare(queue=queue_name)

    # jobs is an index of callback functions. basic_consume, below, read the job_type inside the messages
    # into the queue and calls the corresponding callback function
    callbacks = {
        const.TRIP_ANALYSIS_JOB: execute_trip_analysis_job,
        const.MAP_UPDATE_JOB: execute_map_update_job
    }
    # event manager for job execution
    channel.basic_consume(queue=queue_name,
                          on_message_callback=lambda c, m, p, body:
                          callbacks.get(get_job_type(body))(get_job_data(body)), auto_ack=True)

    logging.info('New Listener subscribed to queue: %s' % queue_name)
    channel.start_consuming()