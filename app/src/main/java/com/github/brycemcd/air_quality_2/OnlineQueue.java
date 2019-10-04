package com.github.brycemcd.air_quality_2;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;

import java.util.LinkedList;
import java.util.List;

// NOTE: this class is overloaded with Cognito Identity and writing to the SQS queue. This
// is the only place where an AWS identity is needed so I'll park it here for now
public class OnlineQueue {
    private static AmazonSQSClient sqsClient;
    private static CognitoCachingCredentialsProvider sCredProvider;
    private static String qURL = "https://sqs.us-east-1.amazonaws.com/304286125266/air_quality_dev";
    protected static String identityPoolId = "us-east-1:c453ed2a-a5bf-418c-b0e2-ab34a81c8e0d";
    static List<SendMessageBatchRequestEntry> msgBatch = new LinkedList<>();

    // FIXME: there's a bug in here where the last batch of messages won't get sent
    // because the total count is not divisible by 10
    public static void addEntry(String msgId, String jsonMsg) {
        SendMessageBatchRequestEntry smbre = new SendMessageBatchRequestEntry(msgId, jsonMsg);
        msgBatch.add(smbre);

        if (msgBatch.size() == 10) flushMsgs();
    }

    public static void flushMsgs() {
        Log.i("BATCH QUEUE", "SENDING BATCH");
        sqsClient.sendMessageBatch(qURL, msgBatch);
        msgBatch = new LinkedList<>();
    }

    /**
     * Gets an instance of a SQS client which is constructed using the given
     * Context.
     *
     * @param context An Context instance.
     * @return A default SQS client.
     */
    public static AmazonSQSClient getSQSClient(Context context) {
        if (sqsClient == null) {
            sqsClient = new AmazonSQSClient(getCredProvider(context.getApplicationContext()));
        }
        return sqsClient;
    }

    /**
     * Gets an instance of CognitoCachingCredentialsProvider which is
     * constructed using the given Context.
     *
     * NOTE: for now, this the online queue is the only reason to have Icognito
     *
     * @param context An Context instance.
     * @return A default credential provider.
     */
    protected static CognitoCachingCredentialsProvider getCredProvider(Context context) {
        if (sCredProvider == null) {
            sCredProvider = new CognitoCachingCredentialsProvider(
                    context.getApplicationContext(),
                    identityPoolId, // Identity pool ID
                    Regions.US_EAST_1);
        }
        return sCredProvider;
    }
}
