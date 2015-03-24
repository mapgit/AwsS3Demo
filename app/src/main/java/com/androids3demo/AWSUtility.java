package com.androids3demo;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;

/**
 * Created by akash on 3/22/15.
 */
public class AWSUtility
{

    // Initialize the Amazon Cognito credentials provider
    public static CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
        AWSApplication.getAppContext(), // Context
        "XXXXXXXXXXXX", //AWS Account ID
        "us-east-1:9gm4jd85-8821-056d-p5k4-7gne49tj5hvb", // Identity Pool ID
        "arn:aws:iam::XXXXXXXXXXXX:role/Cognito_AwsToolsDemoUnauth_DefaultRole", //Unauthenticated role
        "arn:aws:iam::XXXXXXXXXXXX:role/Cognito_AwsToolsDemoAuth_DefaultRole", //Authenticated role
        Regions.US_EAST_1 // Region
    );
}
