package com.akto.testing_issues;

import com.akto.dao.context.Context;
import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.log.LoggerMaker;
import com.akto.testing_utils.TestingUtils;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.akto.util.Constants.ID;
import static com.akto.util.enums.GlobalEnums.*;

public class TestingIssuesHandler {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestingIssuesHandler.class);
    //Update one Write models
    /*
     * Checks the status of issue from db,
     *
     * */

    private void writeUpdateQueryIntoWriteModel(List<WriteModel<TestingRunIssues>> writeModelList,
                                                Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap,
                                                List<TestingRunIssues> testingRunIssuesList) {
        int lastSeen = Context.now();

        testingRunIssuesList.forEach(testingRunIssues -> {
            TestingIssuesId issuesId = testingRunIssues.getId();

            TestingRunResult runResult = testingIssuesIdsMap.get(getIssuesIdFromMap(issuesId, testingIssuesIdsMap));
            if (runResult == null) {
                return;
            }
            TestRunIssueStatus status = testingRunIssues.getTestRunIssueStatus();
            Bson query = Filters.eq(ID, issuesId);
            Bson updateStatusFields;
            Bson updateSeverityField;
            if (runResult.isVulnerable()) {
                if (status == TestRunIssueStatus.IGNORED) {
                    updateStatusFields = Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.IGNORED);
                } else {
                    updateStatusFields = Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN);
                }
            } else {
                updateStatusFields = Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.FIXED);
            }

            TestSubCategory subCategory = TestSubCategory.getTestCategory(runResult.getTestSubType());
            if (subCategory == null) {//TestSourceConfig case
                TestSourceConfig config = TestSourceConfigsDao.instance.getTestSourceConfig(runResult.getTestSubType());
                updateSeverityField = Updates.set(TestingRunIssues.KEY_SEVERITY, config.getSeverity());
            } else {//TestSubCategory case
                updateSeverityField = Updates.set(TestingRunIssues.KEY_SEVERITY,
                        subCategory.getSuperCategory().getSeverity());
            }

            Bson updateFields = Updates.combine(
                    updateStatusFields,
                    updateSeverityField,
                    Updates.set(TestingRunIssues.LAST_SEEN, lastSeen),
                    Updates.set(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID, runResult.getTestRunResultSummaryId())
            );
            loggerMaker.infoAndAddToDb(String.format("Updating the issue with id %s, with update parameters and result_summary_Id :%s ", issuesId
                    ,runResult.getTestRunResultSummaryId()));

            writeModelList.add(new UpdateOneModel<>(query, updateFields));
        });

    }

    private TestingIssuesId getIssuesIdFromMap(TestingIssuesId issuesId,
                                               Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap) {
        for (TestingIssuesId testingIssuesId : testingIssuesIdsMap.keySet()) {
            if (testingIssuesId.equals(issuesId)) {
                return testingIssuesId;
            }
        }
        return new TestingIssuesId();
    }

    private void insertVulnerableTestsIntoIssuesCollection(List<WriteModel<TestingRunIssues>> writeModelList,
                                                           Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap,
                                                           List<TestingRunIssues> testingRunIssuesList) {
        int lastSeen = Context.now();
        testingIssuesIdsMap.forEach((testingIssuesId, runResult) -> {
            boolean doesExists = false;
            for (TestingRunIssues testingRunIssues : testingRunIssuesList) {
                if (testingRunIssues.getId().equals(testingIssuesId)) {
                    doesExists = true;
                    break;
                }
            }
            if (!doesExists && runResult.isVulnerable()) {
                TestSubCategory subCategory = TestSubCategory.getTestCategory(runResult.getTestSubType());
                if (subCategory == null) {
                    TestSourceConfig config = TestSourceConfigsDao.instance.getTestSourceConfig(runResult.getTestSubType());
                    writeModelList.add(new InsertOneModel<>(new TestingRunIssues(testingIssuesId,
                            config.getSeverity(),
                            TestRunIssueStatus.OPEN, lastSeen, lastSeen, runResult.getTestRunResultSummaryId())));
                }else {
                    writeModelList.add(new InsertOneModel<>(new TestingRunIssues(testingIssuesId,
                            subCategory.getSuperCategory().getSeverity(),
                            TestRunIssueStatus.OPEN, lastSeen, lastSeen, runResult.getTestRunResultSummaryId())));
                }
                loggerMaker.infoAndAddToDb(String.format("Inserting the id %s , with summary Id as %s", testingIssuesId, runResult.getTestRunResultSummaryId()));
            }
        });
    }

    public void handleIssuesCreationFromTestingRunResults(List<TestingRunResult> testingRunResultList) {

        Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap = TestingUtils.
                listOfIssuesIdsFromTestingRunResults(testingRunResultList, true);

        loggerMaker.infoAndAddToDb(String.format("Total issue id created from TestingRunResult map : %s", testingIssuesIdsMap.size()));
        Bson inQuery = Filters.in(ID, testingIssuesIdsMap.keySet().toArray());
        List<TestingRunIssues> testingRunIssuesList = TestingRunIssuesDao.instance.findAll(inQuery);

        loggerMaker.infoAndAddToDb(String.format("Total list of issues from db : %s", testingRunIssuesList.size()));
        List<WriteModel<TestingRunIssues>> writeModelList = new ArrayList<>();
        writeUpdateQueryIntoWriteModel(writeModelList, testingIssuesIdsMap, testingRunIssuesList);
        loggerMaker.infoAndAddToDb(String.format("Total write queries after the update iterations: %s", writeModelList.size()));
        insertVulnerableTestsIntoIssuesCollection(writeModelList, testingIssuesIdsMap, testingRunIssuesList);
        loggerMaker.infoAndAddToDb(String.format("Total write queries after the insertion iterations: %s", writeModelList.size()));
        try {
            if (writeModelList.size() > 0) {
                BulkWriteResult result = TestingRunIssuesDao.instance.bulkWrite(writeModelList, new BulkWriteOptions().ordered(false));
                loggerMaker.infoAndAddToDb(String.format("Matched records : %s", result.getMatchedCount()));
                loggerMaker.infoAndAddToDb(String.format("inserted counts : %s", result.getInsertedCount()));
                loggerMaker.infoAndAddToDb(String.format("Modified counts : %s", result.getModifiedCount()));
            } else {
                loggerMaker.infoAndAddToDb("writeModelList is empty");
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(String.format("Error while inserting issues into db: %s", e.toString()));
        }
    }
}
