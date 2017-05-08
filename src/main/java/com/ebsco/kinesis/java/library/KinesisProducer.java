package com.ebsco.kinesis.java.library;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration;
import com.amazonaws.services.kinesis.producer.UserRecordFailedException;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;

/**
 * Created by aganapathy on 5/4/17. This class uses amazon kinesis producer
 * library to publish to kinesis
 */

public class KinesisProducer implements Runnable {

    final static Logger LOG = LoggerFactory.getLogger(KinesisProducer.class);

    private final String STREAM_NAME = "kpl_test_stream";

    protected final static String REGION = "us-east-1";

    protected final BlockingQueue<TransactionLogging> txnLoggingQueue;

    private final com.amazonaws.services.kinesis.producer.KinesisProducer kinesis;

    /**
     * Constructor to instantiate the class with KinesisProducerConfiguration
     * 
     * @param txnLoggingQueue
     */
    public KinesisProducer(BlockingQueue<TransactionLogging> txnLoggingQueue) {
        this.txnLoggingQueue = txnLoggingQueue;
        AWSCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
        KinesisProducerConfiguration config = new KinesisProducerConfiguration();
        config.setRegion(REGION);
        config.setMaxConnections(1);
        config.setRecordMaxBufferedTime(15000);
        config.setCredentialsProvider(credentialsProvider);
        kinesis = new com.amazonaws.services.kinesis.producer.KinesisProducer(config);
    }

    /**
     * This method publishes to kinesis in batch of three
     */

    @Override
    public void run() {
        try {
            while (txnLoggingQueue.size() >= 0) {
                TransactionLogging transactionLogging = txnLoggingQueue.take();
                if (KinesisPublisherImpl.validate(transactionLogging)) {
                    String partitionKey = transactionLogging.getSessionId();
                    String payload = transactionLogging.getPayload();
                    ByteBuffer data = ByteBuffer.wrap(payload.getBytes("UTF-8"));
                    ListenableFuture<UserRecordResult> f = kinesis.addUserRecord(STREAM_NAME, partitionKey, data);
                    while (kinesis.getOutstandingRecordsCount() >= 3) {
                        kinesis.flush();
                    }

                    Futures.addCallback(f, new FutureCallback<UserRecordResult>() {
                        @Override
                        public void onSuccess(UserRecordResult result) {

                            LOG.info((String.format("Succesfully put record, sequenceNumber=%s, " + "shardId=%s",
                                    result.getSequenceNumber(), result.getShardId())));

                        }

                        @Override
                        public void onFailure(Throwable t) {
                            if (t instanceof UserRecordFailedException) {
                                UserRecordFailedException e = (UserRecordFailedException) t;

                                e.printStackTrace();
                                LOG.info(String.format("Record failed to put, partitionKey=%s, " + "payload=%s",
                                        partitionKey, payload));
                            }
                        }

                    });
                }
            }
        }

        catch (Exception e) {
            e.printStackTrace();
        }

    }
}