package com.techsophy.tsf.account.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserPreferencesConstants
{
    public static final String THEMEID="1";

    //INITILIZATION CONSTANTS
    public static final String DEPARTMENT="department";
    public static final String  NULL=null;
    public static final String EMAIL_ID="emailId";
    public static final String MOBILE_NUMBER="mobileNumber";
    public static final String LAST_NAME="lastName";
    public static final String  FIRST_NAME="firstName";
    public static final String USER_NAME="userName";
    public static final String UPDATED_ON="updatedOn";
    public static final String UPDATEDBYNAME ="updatedByName";
    public static final String UPDATEDBYID ="updatedById";
    public static final String CREATEDON ="createdOn";
    public static final String CREATEDBYNAME ="createdByName";
    public static final String CREATED_BY_ID="createdById";
    public static final String TEJASWINI="tejaswini";
    public static final String KAZA="Kaza";
    public static final String NUMBER="1234567890";
    public static final String TEJASWINI_MAIL_ID="tejaswini.k@techsophy.com";
    public static final String ID_NUMBER= "847117072898674688";
    public static final String ID ="id";

    //UserPreferenceControllerCOnstants
    public static final String  FILE="file";
    public static final String IMAGE_PNG="image.png";
    public static final String PICTURE_CONTENT="ABC";
    public static final String IMAGE="Image";

    //UserPrefControllerExceptionTest
    public static final String USER_PREFERENCE_SCHEMA_NOT_FOUND="User Preference Schema not found for given id";

    //UserprefServiceConstants
    public static final String  ONE="1";
    public static final String USER_ID="847117072898674688";
    public static final String PROFILE_PICTURE_NAME="profilePicture";
    public static final String PROFILE_PICTURE_JPEG="profilePicture.jpeg";
    public static final String FILE_NAME="abc";
    public static final String EMPTY_STRING="";
    public static final String ZERO="0";
    public static final String ABC_JPEG="abc.jpeg";
    public static final String IMAGE_CONTENT="123";

    //UserDetailsTestConstants
    public static final String  USER_DETAILS_RETRIEVED_SUCCESS="User details retrieved successfully";
    public static final String  ABC="abc";
    public static final String TEST_TOKEN="token";
    public static final String INITIALIZATION_DATA="{\"data\":[{\"id\":\"847117072898674688\",\"userName\":\"tejaswini\",\"firstName\":\"Kaza\",\"lastName\":\"Tejaswini\",\"mobileNumber\":\"1234567890\",\"emailId\":\"tejaswini.k@techsophy.com\",\"department\":null,\"createdById\":null,\"createdByName\":null,\"createdOn\":null,\"updatedById\":null,\"updatedByName\":null,\"updatedOn\":null}],\"success\":true,\"message\":\"User details retrieved successfully\"}\n";

    //WEBCLIENTWrapperTestConstants
    public static final String LOCAL_HOST_URL="http://localhost:8080";
    public static final String TOKEN="token";
    public static final String TEST="test";
    public static final String PUT="put";
    public static final String DELETE="delete";
}
