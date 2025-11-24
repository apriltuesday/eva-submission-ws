package uk.ac.ebi.eva.submission.controller.submissionws;

import com.elixir.mars.repository.MarsReceiptException;
import com.elixir.mars.repository.models.isa.IsaJson;
import com.elixir.mars.repository.models.receipt.MarsReceipt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.eva.submission.controller.BaseController;
import uk.ac.ebi.eva.submission.entity.Submission;
import uk.ac.ebi.eva.submission.entity.SubmissionAccount;
import uk.ac.ebi.eva.submission.exception.InvalidSubmissionStatusException;
import uk.ac.ebi.eva.submission.exception.MetadataFileInfoMismatchException;
import uk.ac.ebi.eva.submission.exception.RequiredFieldsMissingException;
import uk.ac.ebi.eva.submission.exception.SubmissionDoesNotExistException;
import uk.ac.ebi.eva.submission.exception.UnauthorizedException;
import uk.ac.ebi.eva.submission.model.SubmissionStatus;
import uk.ac.ebi.eva.submission.service.LsriTokenService;
import uk.ac.ebi.eva.submission.service.MarsConversionService;
import uk.ac.ebi.eva.submission.service.SubmissionService;
import uk.ac.ebi.eva.submission.service.WebinTokenService;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/v1")
public class SubmissionController extends BaseController {
    private final Logger logger = LoggerFactory.getLogger(SubmissionController.class);

    public static final String PROJECT = "project";
    public static final String PROJECT_ACCESSION = "projectAccession";
    public static final String TITLE = "title";
    public static final String DESCRIPTION = "description";
    public static final String TAXONOMY_ID = "taxId";
    public static final String ANALYSIS = "analysis";

    private final SubmissionService submissionService;
    private final WebinTokenService webinTokenService;
    private final LsriTokenService lsriTokenService;
    private final MarsConversionService marsConversionService;

    public SubmissionController(SubmissionService submissionService, WebinTokenService webinTokenService,
                                LsriTokenService lsriTokenService, MarsConversionService marsConversionService) {
        super(webinTokenService, lsriTokenService);
        this.submissionService = submissionService;
        this.webinTokenService = webinTokenService;
        this.lsriTokenService = lsriTokenService;
        this.marsConversionService = marsConversionService;
    }

    @Operation(summary = "This endpoint authenticates a user with LSRI")
    @Parameters({
            @Parameter(name = "deviceCode", description = "device code for authentication",
                    required = true, in = ParameterIn.QUERY),
            @Parameter(name = "expiresIn", description = "device code expiration time in seconds",
                    required = true, in = ParameterIn.QUERY)
    })
    @PostMapping("submission/auth/lsri")
    public ResponseEntity<?> authenticateLSRI(@RequestParam("deviceCode") String deviceCode,
                                              @RequestParam("expiresIn") int codeExpirationTimeInSeconds) {
        String token = lsriTokenService.pollForToken(deviceCode, codeExpirationTimeInSeconds);
        if (Objects.nonNull(token)) {
            return new ResponseEntity<>(token, HttpStatus.OK);
        }
        return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
    }

    @Operation(summary = "This endpoint marks the initiation of a submission. It will do the necessary prep work " +
            "for receiving the submission files")
    @Parameters({
            @Parameter(name = "Authorization", description = "Token (bearerToken) for authenticating the user",
                    required = true, in = ParameterIn.HEADER)
    })
    @PostMapping("submission/initiate")
    public ResponseEntity<?> initiateSubmission(@RequestHeader("Authorization") String bearerToken) {
        SubmissionAccount submissionAccount = this.getSubmissionAccount(bearerToken);
        if (Objects.isNull(submissionAccount)) {
            return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        Submission submission = this.submissionService.initiateSubmission(submissionAccount);
        return new ResponseEntity<>(stripUserDetails(submission), HttpStatus.OK);
    }

    @Operation(summary = "Given a submission id, this endpoint will mark the submission as uploaded")
    @Parameters({
            @Parameter(name = "Authorization", description = "Token (bearerToken) for authenticating the user",
                    required = true, in = ParameterIn.HEADER),
            @Parameter(name = "submissionId", description = "Id of the submission whose status needs to be retrieved",
                    required = true, in = ParameterIn.PATH)
    })
    @PutMapping("submission/{submissionId}/uploaded")
    public ResponseEntity<?> markSubmissionUploaded(@RequestHeader("Authorization") String bearerToken,
                                                    @PathVariable("submissionId") String submissionId,
                                                    @RequestBody JsonNode metadataJson) {
        try {
            Submission submission = doSubmission(bearerToken, submissionId, metadataJson);
            return new ResponseEntity<>(stripUserDetails(submission), HttpStatus.OK);
        } catch (UnauthorizedException ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.UNAUTHORIZED);
        } catch (InvalidSubmissionStatusException ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (RequiredFieldsMissingException | MetadataFileInfoMismatchException ex) {
            logger.error("Error occurred while processing the submission.", ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Error occurred while processing the submission.", e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Given a submission id, this endpoint will mark the submission as uploaded by MARS")
    @Parameters({
            @Parameter(name = "Authorization", description = "Token (bearerToken) for authenticating the user",
                    required = true, in = ParameterIn.HEADER),
            @Parameter(name = "submissionId", description = "Id of the submission being marked as uploaded",
                    required = true, in = ParameterIn.PATH)
    })
    @PutMapping("submission/{submissionId}/uploadedByMars")
    public MarsReceipt markSubmissionUploadedByMars(@RequestHeader("Authorization") String bearerToken,
                                                    @PathVariable("submissionId") String submissionId,
                                                    @RequestBody IsaJson marsIsaJson) {
        try {
            JsonNode evaMetadataJson = marsConversionService.convertMarsToEvaJson(marsIsaJson);
            Submission submission = doSubmission(bearerToken, submissionId, evaMetadataJson);
            return marsConversionService.buildMarsSuccessfulReceipt(submission);
        } catch (MarsReceiptException ex) {
            return marsConversionService.buildMarsFailedReceipt(ex);
        } catch (Exception ex) {
            return marsConversionService.buildMarsFailedReceipt(ex.getMessage());
        }
    }

    @Operation(summary = "Given a submission id, this endpoint retrieves the current status of the submission")
    @Parameters({
            @Parameter(name = "submissionId", description = "Id of the submission whose status needs to be retrieved",
                    required = true, in = ParameterIn.PATH)
    })
    @GetMapping("submission/{submissionId}/status")
    public ResponseEntity<?> getSubmissionStatus(@PathVariable("submissionId") String submissionId) {
        try {
            String submissionStatus = submissionService.getSubmissionStatus(submissionId);
            return new ResponseEntity<>(submissionStatus, HttpStatus.OK);
        } catch (SubmissionDoesNotExistException ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    private Submission doSubmission(String bearerToken, String submissionId, JsonNode metadataJson) {
        SubmissionAccount submissionAccount = this.getSubmissionAccount(bearerToken);
        if (Objects.isNull(submissionAccount) || !submissionService.checkUserHasAccessToSubmission(submissionAccount, submissionId)) {
            throw new UnauthorizedException("Unauthorized");
        }

        String submissionStatus = submissionService.getSubmissionStatus(submissionId);
        if (!Objects.equals(submissionStatus, SubmissionStatus.OPEN.toString())) {
            throw new InvalidSubmissionStatusException(
                    "Submission " + submissionId + " is not in status " + SubmissionStatus.OPEN +
                            ". It cannot be marked as " + SubmissionStatus.UPLOADED +
                            ". Current Status: " + submissionStatus);
        }

        // check if there is a difference between the files uploaded and files mentioned in metadata
        submissionService.checkMetadataFileInfoMatchesWithUploadedFiles(submissionAccount, submissionId, metadataJson);

        // check if all the required parameters are present/provided
        Map<String, String> projectDetails = submissionService.checkAllRequiredParametersProvided(metadataJson);

        String projectTitle = projectDetails.get(TITLE);
        String projectDescription = projectDetails.get(DESCRIPTION);
        String projectTaxonomy = projectDetails.get(TAXONOMY_ID);

        // save submission details along with metadata
        Submission submission = this.submissionService.uploadMetadataJsonAndMarkUploaded(submissionId,
                                                                                         projectTitle, projectDescription, metadataJson);

        // check if consent statement is required
        ArrayNode analysisNode = (ArrayNode) metadataJson.get(ANALYSIS);
        boolean needConsentStatement = checkConsentStatementIsNeededForTheSubmission(Integer.parseInt(projectTaxonomy), analysisNode);

        // send notification to user
        submissionService.sendMailNotificationToUserForStatusUpdate(submissionAccount, submissionId, projectTitle,
                                                                    SubmissionStatus.UPLOADED, needConsentStatement, true);
        // send notification to EVA HelpDesk
        submissionService.sendMailNotificationToEVAHelpdeskForSubmissionUploaded(submissionAccount, submissionId,
                                                                                 projectTitle);

        return submission;
    }

    private boolean checkConsentStatementIsNeededForTheSubmission(int taxonomyId, ArrayNode analysisNode) {
        if (taxonomyId != 9606) {
            return false;
        }

        Set<String> evidenceTypes = new HashSet<>();
        for (JsonNode node : analysisNode) {
            String evidenceType = node.path("evidenceType").asText(null);
            if (evidenceType != null) {
                evidenceTypes.add(evidenceType);
            }
        }

        if (evidenceTypes.size() == 0 || evidenceTypes.contains("genotype")) {
            return true;
        }

        return false;
    }
}
