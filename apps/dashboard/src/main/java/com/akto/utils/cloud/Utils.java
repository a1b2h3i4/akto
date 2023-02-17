package com.akto.utils.cloud;

import com.akto.log.LoggerMaker;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Tag;

import java.util.List;

public class Utils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Utils.class);
    public static CloudType getCloudType() {
        if (System.getenv("AWS_REGION") != null) {
            return CloudType.AWS;
        }
        return CloudType.GCP;
    }

    public static List<Tag> fetchTags(String stackName){
        DescribeStacksRequest describeStackRequest = new DescribeStacksRequest();
        describeStackRequest.setStackName(stackName);
        AmazonCloudFormation cloudFormation = AmazonCloudFormationClientBuilder.standard()
                .build();
        DescribeStacksResult result = cloudFormation.describeStacks(describeStackRequest);
        com.amazonaws.services.cloudformation.model.Stack stack = result.getStacks().get(0);
        return stack.getTags();
    }

}
