package centralcasting;

import java.io.File;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;

public class AWSSecureUpload 
{
    private String userId;  //user
    private String uploadFolder;  //folder in aws s3 to upload to; only for testing purposes
    private String bucket = "the-ep-project-bucket";    //the fixed bucket to be uploaded to
    private File filePath;    //the path of the file to be uploaded
    private AssumeRoleResponse tempCredentials;   //the temporary credentials of the user
    private S3Client s3;

    //constructor to initialize the name and folder
    public AWSSecureUpload(String uId, String folder){
        
        userId = uId;
        uploadFolder = folder;
    }

    //function to input the path of the file to be uploaded
    public void getFilePath(){

        String path = System.console().readLine("Enter the path of the file to be uploaded : ");
        filePath = new File(path);

    }

    private void getCredentials(){

        //session name is unique(?)
        
        String sessionName = userId + System.currentTimeMillis();
        //the policy document
        String policy_doc = "{" +
        "    \"Version\": \"2012-10-17\"," +
        "    \"Statement\":[" +
        "       {" +
        "         \"Sid\" : \"ListYourOwnFolder\"," +
        "         \"Action\" : [" +
        "              \"s3:ListBucket\"" +
        "            ]," +
        "          \"Effect\" : \"Allow\"," +
        "          \"Resource\" : [" +
        "              \"arn:aws:s3:::the-ep-project-bucket\"" +
        "            ]," +
        "          \"Condition\" : {" +
        "             \"StringLike\": {" +
        "                \"s3:prefix\" : [\"" + uploadFolder + "/*\"]" +
        "            }" +
        "          }" +
        "       }," +
        "       {" +
        "         \"Sid\" : \"FullAccessInOwnFolder\"," + 
        "         \"Action\" : [" +
        "            \"s3:GetObject\"," + 
        "            \"s3:PutObject\"," +
        "            \"s3:DeleteObject\"" + 
        "        ]," +
        "        \"Effect\" : \"Allow\"," +
        "        \"Resource\" : [" +
        "            \"arn:aws:s3:::the-ep-project-bucket/" + uploadFolder + "/*\"" +
        "        ]" +
        "       }," +
        "       {" +
        "        \"Sid\" : \"ExplicitlyDenyAccessToOtherFolder\"," +
        "        \"Action\" : [" +
        "            \"s3:ListBucket\"" +
        "        ]," +
        "        \"Effect\" : \"Deny\"," +
        "        \"Resource\" : [" +
        "            \"arn:aws:s3:::the-ep-project-bucket\"" +
        "        ]," +
        "        \"Condition\" : {" +
        "            \"StringNotLike\": {" +
        "                \"s3:prefix\": [" +
        "                    \"" + uploadFolder + "/*\"," +
        "                    \"\"" +
        "                    ]" + 
        "                }," +
        "                \"Null\" : {" +
        "                    \"s3:prefix\" : false" +
        "                }" +
        "           }" +
        "       }" +
        "    ]" +
        " }";

        StsClient sts = StsClient.create();

        AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
                                                            .durationSeconds(900)
                                                            .externalId("123")
                                                            .roleArn("arn:aws:iam::624723861307:role/sample_role")
                                                            .roleSessionName(sessionName)
                                                            .policy(policy_doc)
                                                            .build();

        AssumeRoleResponse assumeRoleResponse = sts.assumeRole(assumeRoleRequest);
        sts.close();

        tempCredentials = assumeRoleResponse;

    }

    //function to start the s3 connection with temporary credentials
    public void createS3Connection(){

        AwsSessionCredentials awsSessionCredentials = AwsSessionCredentials.create(
                                                                                tempCredentials.credentials().accessKeyId(), 
                                                                                tempCredentials.credentials().secretAccessKey(), 
                                                                                tempCredentials.credentials().sessionToken()
                                                                                );

        //s3 client with the temporary credentials
        s3 = S3Client.builder()
                              .credentialsProvider(StaticCredentialsProvider.create(awsSessionCredentials))
                              .region(Region.US_EAST_1)
                              .build();

    }

    //function to upload a file into s3
    public void uploadFileToS3(){

        String key = userId + "/" + filePath.getName();   //where to put the file in the s3 bucket
        
        // AwsSessionCredentials awsSessionCredentials = AwsSessionCredentials.create(
        //                                                                         tempCredentials.credentials().accessKeyId(), 
        //                                                                         tempCredentials.credentials().secretAccessKey(), 
        //                                                                         tempCredentials.credentials().sessionToken()
        //                                                                         );

        // //s3 client with the temporary credentials
        // S3Client s3 = S3Client.builder()
        //                       .credentialsProvider(StaticCredentialsProvider.create(awsSessionCredentials))
        //                       .build();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucket(bucket).key(key).build();
        PutObjectResponse putObjectResponse = s3.putObject(putObjectRequest, RequestBody.fromFile(filePath));

        System.out.println("File " + filePath.getName() + " Uploaded ! ");

        //s3.close();
    }

    public void listFolder(){

        String prefix = userId + "/";

        ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
                                                                    .bucket(bucket)
                                                                    .prefix(prefix)
                                                                    .build();

        ListObjectsV2Response listObjectsResponse = s3.listObjectsV2(listObjectsRequest);
        listObjectsResponse.contents().forEach((x) -> System.out.println(x.key()));

    }

    public void closeS3Connection(){
        s3.close();
    }

    public static void main( String[] args )
    {
        AWSSecureUpload aws = new AWSSecureUpload("mohana", "mohana");
        aws.getCredentials();
        aws.getFilePath();
        aws.createS3Connection();
        aws.uploadFileToS3();
        aws.listFolder();
        aws.closeS3Connection();
        //aws.listFolder();
    }
}
