package com.akto.action;

import com.akto.dao.AccountsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.UserAccountEntry;
import com.akto.log.LoggerMaker;
import com.akto.utils.cloud.serverless.aws.Lambda;
import com.akto.utils.cloud.stack.Stack;
import com.akto.utils.cloud.stack.aws.AwsStack;
import com.akto.utils.cloud.stack.dto.StackState;
import com.akto.utils.platform.MirroringStackDetails;
import com.amazonaws.services.lambda.model.*;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

public class AccountAction extends UserAction {

    private String newAccountName;
    private int newAccountId;
    private static final LoggerMaker loggerMaker = new LoggerMaker(AccountAction.class);

    public static final int MAX_NUM_OF_LAMBDAS_TO_FETCH = 50;

    @Override
    public String execute() {

        return Action.SUCCESS.toUpperCase();
    }

    private void invokeExactLambda(String functionName, AWSLambda awsLambda) {

        InvokeRequest invokeRequest = new InvokeRequest()
            .withFunctionName(functionName)
            .withPayload("{}");
        InvokeResult invokeResult = null;
        try {

            loggerMaker.infoAndAddToDb("Invoke lambda "+functionName);
            invokeResult = awsLambda.invoke(invokeRequest);

            String resp = new String(invokeResult.getPayload().array(), StandardCharsets.UTF_8);
            loggerMaker.infoAndAddToDb(String.format("Function: %s, response: %s", functionName, resp));
        } catch (AWSLambdaException e) {
            loggerMaker.errorAndAddToDb(String.format("Error while invoking Lambda, %s : %s", functionName, e));
        }
    }

    private void listMatchingLambda(String functionName) {
        AWSLambda awsLambda = AWSLambdaClientBuilder.standard().build();
        try {
            ListFunctionsRequest request = new ListFunctionsRequest();
            request.setMaxItems(MAX_NUM_OF_LAMBDAS_TO_FETCH);

            boolean done = false;
            while(!done){
                ListFunctionsResult functionResult = awsLambda
                        .listFunctions(request);
                List<FunctionConfiguration> list = functionResult.getFunctions();
                loggerMaker.infoAndAddToDb(String.format("Found %s functions", list.size()));

                for (FunctionConfiguration config: list) {
                    loggerMaker.infoAndAddToDb(String.format("Found function: %s",config.getFunctionName()));

                    if(config.getFunctionName().contains(functionName)) {
                        loggerMaker.infoAndAddToDb(String.format("Invoking function: %s", config.getFunctionName()));
                        invokeExactLambda(config.getFunctionName(), awsLambda);
                    }
                }

                if(functionResult.getNextMarker() == null){
                    done = true;
                }
                request = new ListFunctionsRequest();
                request.setMaxItems(MAX_NUM_OF_LAMBDAS_TO_FETCH);
                request.setMarker(functionResult.getNextMarker());
            }


        } catch (AWSLambdaException e) {
            loggerMaker.errorAndAddToDb(String.format("Error while updating Akto: %s",e));
        }
    }

    public String takeUpdate() {
        if(checkIfStairwayInstallation()) {
            loggerMaker.infoAndAddToDb("This is a stairway installation, invoking lambdas now");
            String lambda;
            try {
                lambda = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_CONTEXT_ANALYZER_UPDATE_LAMBDA);
                Lambda.getInstance().invokeFunction(lambda);
                loggerMaker.infoAndAddToDb(String.format("Successfully invoked lambda %s", lambda));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(String.format("Failed to update Akto Context Analyzer %s", e));
            }
            try{
                lambda = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(),MirroringStackDetails.AKTO_DASHBOARD_UPDATE_LAMBDA);
                Lambda.getInstance().invokeFunction(lambda);
                loggerMaker.infoAndAddToDb(String.format("Successfully invoked lambda %s", lambda));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(String.format("Failed to update Akto Dashboard", e));
            }
                
            try{
                lambda = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_RUNTIME_UPDATE_LAMBDA);
                Lambda.getInstance().invokeFunction(lambda);
                loggerMaker.infoAndAddToDb(String.format("Successfully invoked lambda {}", lambda));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(String.format("Failed to update Akto Traffic Mirroring Instance", e));
            }
        } else {
            loggerMaker.infoAndAddToDb("This is an old installation, updating via old way");
            listMatchingLambda("InstanceRefresh");
        }
        
        return Action.SUCCESS.toUpperCase();
    }

    private boolean checkIfStairwayInstallation() {
        StackState stackStatus = AwsStack.getInstance().fetchStackStatus(MirroringStackDetails.getStackName());
        return "CREATE_COMPLETE".equalsIgnoreCase(stackStatus.getStatus().toString());
    }

    public String createNewAccount() {
        newAccountId = Context.getId();
        AccountsDao.instance.insertOne(new Account(newAccountId, newAccountName));

        UserAccountEntry uae = new UserAccountEntry();
        uae.setAccountId(newAccountId);
        BasicDBObject set = new BasicDBObject("$set", new BasicDBObject("accounts."+newAccountId, uae));

        UsersDao.instance.getMCollection().updateOne(eq("login", getSUser().getLogin()), set);

        getSession().put("accountId", newAccountId);
        Context.accountId.set(newAccountId);

        return Action.SUCCESS.toUpperCase();
    }

    public String goToAccount() {
        if (getSUser().getAccounts().containsKey(newAccountId+"")) {
            getSession().put("accountId", newAccountId);
            Context.accountId.set(newAccountId);
            return SUCCESS.toUpperCase();
        }

        return ERROR.toUpperCase();
    }

    public String getNewAccountName() {
        return newAccountName;
    }

    public void setNewAccountName(String newAccountName) {
        this.newAccountName = newAccountName;
    }

    public int getNewAccountId() {
        return newAccountId;
    }

    public void setNewAccountId(int newAccountId) {
        this.newAccountId = newAccountId;
    }
}
