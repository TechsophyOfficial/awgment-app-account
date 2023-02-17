package com.techsophy.tsf.account.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AccountConstants
{
 //DatabaseChangeLog
 public static final String ORDER_1="1";
 public static final String ORDER_2="100";
 public static final String ORDER_3="101";
 public static final String ORDER_4="102";
 public static final String ORDER_5="103";
 public static final String SYSTEM_VERSION_1="1";
 public static final String ADD_ADMIN_USER= "add-admin-user01";
 public static final String CHANGE_ALL_USER_NAMES_TO_LOWER= "change-user-names-to-lowercase";
 public static final String ADD_SYSTEM_USER= "add-system-user02";
 public static final String ADD_FORM_SYSTEM_USER= "add-form_system-user01";
 public static final String ADD_FORMDATA_ADMIN_USER= "add-formdata-admin-user01";
 public static final String TP_FORMDATA_USER_JSON ="TP_FORMDATA_USER.json";
 public static final String TP_FORMDATA_SYSTEM_USER_JSON ="TP_FORMDATA_SYSTEM_USER.json";
 public static final String TP_USER_JSON = "TP_USER.json";
 public static final String TP_SYSTEM_USER_JSON = "TP_SYSTEM_USER.json";
 public static final String TP_USER_THEME_JSON = "TP_USER_THEME.json";
 public static final String TP_THEME_JSON = "TP_THEME.json";
 public static final String ADD_THEME= "add-theme";
 public static final String USER_DATA_NAME= "userName";
 public static final String EMAIL_ID= "emailId";
 public static final String ID_VALUE= "821024382412341232";



 //JWTRoleConverter
 public static final String CLIENT_ROLES="clientRoles";
 public static final String USER_INFO_URL= "/protocol/openid-connect/userinfo";
 public static final String TOKEN_VERIFICATION_FAILED="Token verification failed";
 public static final String AWGMENT_ROLES_MISSING_IN_CLIENT_ROLES ="AwgmentRoles are missing in clientRoles";
 public static final String CLIENT_ROLES_MISSING_IN_USER_INFORMATION="ClientRoles are missing in the userInformation";

 //LoggingHandler
 public static final String CONTROLLER_CLASS_PATH = "execution(* com.techsophy.tsf.account.controller..*(..))";
 public static final String SERVICE_CLASS_PATH= "execution(* com.techsophy.tsf.account.service..*(..))";
 public static final String EXCEPTION = "ex";
 public static final String IS_INVOKED_IN_CONTROLLER= "{}() is invoked in controller ";
 public static final String IS_INVOKED_IN_SERVICE= "{}() is invoked in service ";
 public static final String EXECUTION_IS_COMPLETED_IN_CONTROLLER="{}() execution is completed  in controller";
 public static final String EXECUTION_IS_COMPLETED_IN_SERVICE="{}() execution is completed  in service";
 public static final String EXCEPTION_THROWN="An exception has been thrown in {} ";
 public static final String CAUSE="Cause : {} ";
 public static final String BRACKETS_IN_CONTROLLER="{}() in controller";
 public static final String BRACKETS_IN_SERVICE="{}() in service";

 /*LocaleConfig Constants*/
 public static final String ACCEPT_LANGUAGE = "Accept-Language";
 public static final String BASENAME_ERROR_MESSAGES = "classpath:errorMessages";
 public static final String BASENAME_MESSAGES = "classpath:messages";
 public static final Long CACHEMILLIS = 3600L;
 public static final Boolean USEDEFAULTCODEMESSAGE = true;

 // Roles
 public static final String HAS_ANY_AUTHORITY="hasAnyAuthority('";
 public static final String HAS_ANY_AUTHORITY_ENDING="')";
 public static final String AWGMENT_ACCOUNT_CREATE_OR_UPDATE = "awgment-account-create-or-update";
 public static final String AWGMENT_ACCOUNT_READ = "awgment-account-read";
 public static final String AWGMENT_ACCOUNT_DELETE = "awgment-account-delete";
 public static final String AWGMENT_ACCOUNT_ALL="awgment-account-all";
 public static final String OR=" or ";
 public static final String CREATE_OR_ALL_ACCESS =HAS_ANY_AUTHORITY+ AWGMENT_ACCOUNT_CREATE_OR_UPDATE +HAS_ANY_AUTHORITY_ENDING+OR+HAS_ANY_AUTHORITY+AWGMENT_ACCOUNT_ALL+HAS_ANY_AUTHORITY_ENDING;
 public static final String READ_OR_ALL_ACCESS =HAS_ANY_AUTHORITY+ AWGMENT_ACCOUNT_READ +HAS_ANY_AUTHORITY_ENDING+OR+HAS_ANY_AUTHORITY+AWGMENT_ACCOUNT_ALL+HAS_ANY_AUTHORITY_ENDING;
 public static final String DELETE_OR_ALL_ACCESS =HAS_ANY_AUTHORITY+AWGMENT_ACCOUNT_DELETE+HAS_ANY_AUTHORITY_ENDING+OR+HAS_ANY_AUTHORITY+AWGMENT_ACCOUNT_ALL+HAS_ANY_AUTHORITY_ENDING;

 /*TenantAuthenticationManagerConstants*/
 public static final String KEYCLOAK_ISSUER_URI = "${keycloak.issuer-uri}";

 /*TokenConfigConstants*/
 public static final String AUTHORIZATION="Authorization";

 /*ThemesControllerImplConstants*/
 public static final String FILE="file";
 public static final String SAVE_THEME_SUCCESS="SAVE.THEME.SUCCESS";
 public static final String GET_THEME_SUCCESS="GET.THEME.SUCCESS";
 public static final String GET_ALL_THEMES_SUCCESS="GET.ALL.THEMES.SUCCESS";
 public static final String DELETE_THEME_SUCCESS="DELETE.THEME.SUCCESS";
 public static final String UPLOAD_THEME_SUCCESS="UPLOAD.THEME.SUCCESS";

 /*UserManagementInKeycloakControllerImplConstants*/
 public static final String USER_NAME="username";
 public static final String USER_NAME_DATA="userName";
 public static final String OLD_USER_NAME="oldUserName";

 public static final String SAVE_USER_SUCCESS="SAVE.USER.SUCCESS";
 public static final String ROLE_USER_SUCCESS="ROLE.USER.SUCCESS";
 public static final String GET_ROLE_SUCCESS="GET.ROLE.SUCCESS";
 public static final String GROUPS_USER_SUCCESS="GROUPS.USER.SUCCESS";
 public static final String DELETE_USER_SUCCESS="DELETE.USER.SUCCESS";
 public static final String PASSWORD_UPDATED_SUCCESSFULLY="PASSWORD.UPDATED.SUCCESS";
 public static final String PASSWORD_SET_SUCCESSFULLY="PASSWORD.SET.SUCCESS";


 /*GroupsControllerConstants*/
 public static final String VERSION_V1="/v1";
 public static final String KEYCLOAK_URL ="/keycloak";
 public static final String GROUPS_URL="/groups";
 public static final String GROUP_BY_ID_URL ="/groups/{id}";
 public static final String PAGE="page";
 public static final String SIZE="size";
 public static final String SORT_BY="sort-by";
 public static final String SORT_ORDER="sort-order";
 public static final String ID="id";
 public static final String QUERY ="q";

 /*GroupsDataControllerConstants*/
 public static final String GROUP_BY_ID_ROLES_URL ="/groups/{id}/roles";

 /*ThemesControllerConstants*/
 public static final String THEMES_URL = "/themes";
 public static final String BY_ID_URL ="/{id}";
 public static final String EXPORT_BY_ID_URL ="/{id}/export";
 public static final String IMPORT_URL ="/import";
 public static final String NAME="name";

/*LoggedInUserConstants*/
public static final String LOGGED_IN_USER ="/loggedIn";


 /*UserFormDataConstants*/
 public static final String USERS_URL ="/users";
 public static final String USER_ID_URL="/{userId}";
 public static final String USER_ID="userId";
 public static final String ONLY_MANDATORY_FIELDS="only-mandatory-fields";
 public static final String FILTER_VALUE="filter-value";
 public static final String FILTER_COLUMN_NAME="filter-column";
 public static final String BULK_UPLOAD_URL="/bulk-upload";
 public static final String OTP_URL="/otp";
 public static final String GENERATE="/generate";
 public static final String VALIDATE="/validate";
 public static final String GROUPS="groups";
 public static final String ROLES="roles";
 public static final String PROCESS_DEFINITION_KEY="processDefinitionKey";
 public static final String BUSINESS_KEY="businessKey";
 public static final String VARIABLES="variables";
 public static final String PROCESS_ID="Process_i8koxaq1";
 public static final String DOCUMENT_ID ="documentId";
 public static final String WORKFLOW_START_URL="/workflow/v1/process/start";
 public static final String COMMA=",";
 public static final String DELETE_FORM_SUCCESS="DELETE.FORM.SUCCESS";
 public static final String SAVE_FORM_SUCCESS="SAVE.FORM.SUCCESS";

 //GroupsControllerConstants
 public static final String BASE_URL= "/accounts";
 public static final String CAPITAL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
 public static final String SMALL_CHARS = "abcdefghijklmnopqrstuvwxyz";
 public static final String NUMBERS = "0123456789";
 public static final String GET_GROUP_SUCCESS="GET.GROUP.SUCCESS";
 public static final String GET_GROUP_BY_ID_SUCCESS="GET.GROUP.BY.ID.SUCCESS";
 public static final String  GROUP_UPDATE_SUCCESS="GROUP.UPDATE.SUCCESS";
 public static final String GROUP_CREATE_SUCCESS="GROUP.CREATE.SUCCESS";
 public static final String DELETE_GROUP_SUCCESS="DELETE.GROUP.SUCCESS";
 public static final String ROLES_ASSIGN_SUCCESS_TO_GROUPS="ASSIGN.ROLES.TO.GROUPS.SUCCESS";

 //UserFormDataControllerConstants
 public static final String ACCOUNTS_URL = "/accounts";
 public static final String GET_FORM_SUCCESS="GET.FORM.SUCCESS";
 public static final String BULK_UPLOAD_SUCCESS="BULK.UPLOAD.SUCCESS";
 public static final String GET_ALL_USERS_SUCCESS="GET.ALL.USERS.SUCCESS";
 public static final String BULK_URL ="/bulk-upload";
 public static final String DELETE_BY_ID="/bulk-upload";
 public static final String BULK_STATUS_URL="/bulk-upload/status";
 public static final String BULK_STATUS_UPDATE_SUCCESS ="BULK.STATUS.UPDATED.SUCCESSFULLY";

 //OTPControllerImpl
 public static final String OTP_GENERATED_SUCCESSFULLY= "OTP.GENERATED.SUCCESSFULLY";
 public static final String OTP_VALIDATED_SUCCESSFULLY="OTP.VALIDATED.SUCCESSFULLY";

 /*UserMgmtInKeycloakControllerConstants*/
 public static final String USER_ROLES_URL="/users/roles";
 public static final String ROLES_URL="/roles";
 public static final String USER_GROUPS_URL="/users/groups";
 public static final String CHANGE_PASSWORD="/users/changePassword";
 public static final String SET_PASSWORD="/users/setPassword";


 /*UserPreferencesControllerConstants*/
 public static final String USER_PREFERENCES_URL="/users/preferences";
 public static final String PROFILE_PICTURE_URL="/profile-picture";
 public static final String USER_PREFERENCE_THEME_SAVED_SUCCESS="USER.PREFERENCE.THEME.SAVED.SUCCESS";
 public static final String GET_USER_PREFERENCE_THEME_SUCCESS="GET.USER.PREFERENCE.THEME.SUCCESS";
 public static final String DELETE_USER_PREFERENCE_THEME_SUCCESS= "DELETE.USER.PREFERENCE.THEME.SUCCESS";
 public static final String USER_PREFERENCE_PROFILE_PHOTO_SAVED_SUCCESS="USER.PREFERENCE.PROFILE.PHOTO.SAVED.SUCCESS";
 public static final String DELETE_USER_PROFILE_PICTURE_SUCCESS="DELETE.USER.PROFILE.PICTURE.SUCCESS";

 /*GroupDataConstants*/
 public static final String GROUP_ID_NOT_BLANK="id cannot be blank";

 /*ThemesSchemaConstants*/
 public static final String NAME_NOT_BLANK="name cannot be blank";

 /*UserFormDataSchemaConstants*/
 public static final  String USER_DATA_NOT_EMPTY="userData cannot be empty";

 /*UserGroupsSchemaConstants*/
 public static final String USER_ID_NOT_NULL="userId cannot be null";

 /*UserPreferencesSchemaConstants*/
 public static final String THEME_ID_NOT_NULL="id cannot be blank";

 /*UserSchemaConstants*/
 public static final String USER_NAME_NOT_NULL= "userName cannot be null";
 public static final String USER_FIRST_NAME_NOT_BLANK ="First name cannot be blank";
 public static final String USER_LAST_NAME_NOT_BLANK="Last name cannot be blank";
 public static final String MOBILE_NUMBER_NOT_BLANK="User mobileNumber cannot be blank";
 public static final String EMAIL_ID_NOT_BLANK="EmailId cannot be blank";
 public static final String DEPARTMENT_NAME_NOT_BLANK="DepartmentName cannot be blank";

 /*GroupDefinitionConstants*/
 public static final String ID_NOT_NULL="Id cannot be null";
 public static final String DESCRIPTION_NOT_BLANK="Description cannot be blank";
 public static final String TP_GROUP_COLLECTION="tp_group";

 /*ThemesDefinitionConstants*/
 public static final String TP_THEME_COLLECTION ="tp_theme";
 public static final String THEME_ID ="themeId";
 public static final String THEME_NAME_NOT_BLANK="name cannot be blank";

 /*UserDefinitionConstants*/
 public static final String TP_USER_COLLECTION="tp_user";

 /*UserFormDataDefinitionConstants*/
 public static final String TP_FORM_DATA_USER_COLLECTION ="tp_formdata_user";
 public static final String VERSION_NOT_NULL="version cannot be null";

 //BulkUserDefinitionConstants
 public static final String TP_USER_BULK_UPLOAD="tp_user_bulk_upload";

 /*UserPreferenceDefinitionConstants*/
 public static final String TP_USER_PREFERENCE_COLLECTION="tp_user_preferences";

 /*ApiErrorResponseConstants*/
 public static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
 public static final String TIME_ZONE = "UTC";
 public static final String DEFAULT_PAGE_LIMIT= "${default.pagelimit}";
 public static final String EMAIL_VALIDITY= "${email.validity}";
 public static final String CLIENTS="${clients}";


 /*ThemesServiceImplConstants*/
 public static final String FIRST_NAME="firstName";
 public static final String LAST_NAME="lastName";
 public static final String GATEWAY_URI="${gateway.uri}";
 public static final String ACCOUNTS_V1_THEMES_URL ="/accounts/v1/themes/";
 public static final String THEME_NAME="name";
 public static final String THEME_CONTENT="content";
 public static final String DOCUMENT_DOWNLOAD="END: Document download - completed successfully";
 public static final String CONTENT_TYPE="Content-Type";
 public static final String APPLICATION_JSON="application/json";
 public static final String CONTENT_DISPOSITION="Content-Disposition";
 public static final String ATTACHMENT_FILE_NAME="attachment; filename=\"";
 public static final String JSON_URL=".json" + "\"";
 public static final String DATA="data";

 //ThemesCustomRepo
 public static final String UNDERSCORE_ID="_id";

 /*UserMgmtInKeycloakServiceImplConstants*/
 public static final String USER_MANAGEMENT_KEYCLOAK_API ="${user.management.keycloak-api}";
 public static final String USER_MANAGEMENT_USER_API="${user.management.user-api}";
 public static final String USER_MANAGEMENT_ROLES_API="${user.management.roles-api}";
 public static final String USER_MANAGEMENT_COUNT_API="${user.management.count-api}";
 public static final String USER_SCHEMA_FIRST_NAME="firstName";
 public static final String USER_SCHEMA_LAST_NAME="lastName";
 public static final String USER_SCHEMA_EMAIL="email";
 public static final String USER_SCHEMA_USER_NAME="username";
 public static final String USER_SCHEMA_ENABLED="enabled";
 public static final String USER_SCHEMA_TYPE="type";
 public static final String USER_SCHEMA_PASSWORD="password";
 public static final String USER_SCHEMA_VALUE="value";
 public static final String USER_SCHEMA_TEMPORARY="temporary";
 public static final String USER_SCHEMA_CREDENTIALS="credentials";
 public static final String USER_SCHEMA_REQUIRED_ACTIONS="requiredActions";
 public static final String USER_SCHEMA_UPDATE_PASSWORD="UPDATE_PASSWORD";
 public static final String GROUP_NAME= "name";
 public static final String DEPLOYMENT="deployment";
 public static final String DEFAULT_GROUPS="defaultGroups";
 public static final String DESCENDING="desc";
 public static final String DEFAULT_ROLES="defaultRoles";
 public static final String GET_ALL_CLIENTS_URL="/clients";
 public static final String CLIENT_ID="clientId";
 public static final String GET_CLIENT_ROLES_URL="/clients/";
 public static final String USERCREDENTIALS="User Credentials - username : ";
 public static final String USERNAME="username";
 public static final String PASSWORD=",  password : ";
 public static final String VALUE="value";
 public static final String EMAIL_NAME=", email : ";

 //UserPreferencesThemeServiceImplConstants
 public static final String JPG_TYPE = "jpg";
 public static final String JPEG_TYPE = "jpeg";
 public static final String PNG_TYPE = "png";
 public static final String UPLOAD_VALID_FILE_TYPES="Please upload valid file types: ";
 public static final String FILE_UPLOAD_STARTED_WITH_NAME="Uploading the file - {} has started with name {} ";

 /*UserServiceImplConstants*/
 public static final String SPACE=" ";
 public static final String LOGIN_ID="LOGINID";
 public static final String SERVICE_ACCOUNT="service-account";
 public static final String DEFAULT_THEME_ID="979654592245444123";
 public static final String DEFAULT_THEME="default-theme";

 //GroupsCustomRepositoryConstants
 public static final String GROUP_DEFINITION_NAME ="name";
 public static final String GROUP_DEFINITION_DESCRIPTION="description";

 /*UserDefinitionCustomRepository*/
 public static final String USER_DEFINITION_NAME="name";
 public static final String USER_DEFINITION_USER_NAME="userName";
 public static final String USER_DEFINITION_FIRST_NAME ="firstName";
 public static final String USER_DEFINITION_LAST_NAME ="lastName";
 public static final String USER_DEFINITION_MOBILE_NUMBER ="mobileNumber";
 public static final String USER_DEFINITION_EMAIL_ID = "emailId";
 public static final String USER_DEFINITION_DEPARTMENT ="department";

 //UserFormDataDefinitionCustomRepositoryConstants
 public static final String USER_DEFINITION_ID="_id";
 public static final String SYSTEM="SYSTEM";
 public static final String USER_DATA_USER_NAME="userData.userName";
 public static final String USER_DATA_FIRST_NAME="userData.firstName";
 public static final String USER_DATA_LAST_NAME="userData.lastName";
 public static final String USER_DATA_MOBILE_NUMBER="userData.mobileNumber";
 public static final String USER_DATA_EMAIL_ID = "userData.emailId";
 public static final String USER_DATA_DEPARTMENT="userData.department";
 public static final String USER_DATA_COMPANY="userData.company";
 public static final String USER_DATA="userData";
 public static final String COLLATION_EN="en";

 /*GroupsDataServiceImplConstants*/
 public static final String USER_MANAGEMENT_GROUPS_API="${user.management.groups-api}";
 public static final String  URL_SEPERATOR="/";
 public static final String ERROR= "error";
 public static final String REALM_ROLES= "realmRoles";
 public static final String ROLE_NAME ="name";
 public static final String ROLE_MAPPINGS_REALM_URL ="/role-mappings/realm";
 public static final String ROLE_MAPPINGS_CLIENT_URL="/role-mappings/clients/";
 public static final String GROUPS_DEFINITION_ID="_id";

 //TokenUtilsConstants
 public static final String BEARER= "Bearer ";
 public static final String EXEC_ACTIONS= "/execute-actions-email?lifespan=";
 public static final String REGEX_SPLIT="\\.";
 public static final String AUTHENTICATION_FAILED="Authentication failed";
 public static final String UNABLE_GET_TOKEN="Unable to get token";
 public static final String PREFERED_USERNAME="preferred_username";
 public static final String CREATED_ON="createdOn";
 public static final String CREATED_BY_ID="createById";
 public static final String CREATED_BY_NAME="createdByName";
 public static final String UPDATED_BY_ID="updatedById";
 public static final String UPDATED_ON="updatedOn";
 public static final String UPDATED_BY_NAME="updatedByName";
 public static final String  COLON=":";
 public static final String ISS="iss";

 //UserDetailsConstants
 public static final String TOKEN_NOT_NULL="token should not be null";
 public static final String ACCOUNT_URL = "/accounts/v1/users";
 public static final String FILTER_COLUMN="?filter-column=loginId&filter-value=";
 public static final String  ONLY_MANDATORY_FIELDS_TRUE="&only-mandatory-fields=true";

 //WebClientWrapperConstants
 public static final String GET="GET";
 public static final String PUT="PUT";
 public static final String DELETE="DELETE";
 public static final String POST="POST";
 public static final String BRIEF_REPRESENTATION="briefRepresentation";
 public static final String EXACT="exact";
 public static final String FIRST="first";
 public static final String MAX="max";
 public static final String SEARCH="search";

 //UserManagementInKeycloakImpl
 public static final String SLASH="/";
 public static final String BRIEF_URL="?briefRepresentation=true&exact=true&username=";

 //UserFormDataServiceImplConstants
 public static final String  DOT=".";
 public static final String UPLOADING_CSV_FILE="Uploading CSV file";
 public static final String CSV = "csv";
 public static final String USER_INFO_MISSING="User information missing ";
 public static final String ROW_AND=" row and ";
 public static final String COLUMN_IN_BULK_UPLOAD_USER="column in bulk upload CSV file ";
 public static final String CREATED="created";
 public static final String UPDATED="updated";
 public static final String STATUS="status";
 public static final String USER_REGISTRATION_FAILURE_EMAIL_TO="${USER.REGISTRATION.FAILURE.EMAIL.TO}";
 public static final String USER_REGISTRATION_FAILURE_EMAIL_SUBJECT="${USER.REGISTRATION.FAILURE.EMAIL.SUBJECT}";
 public static final String FILE_VALIDATIONS_GOING_ON="File validations going on";
 public static final String CALLING_BPMN="Calling BPMN";
 public static final String USER_REG_FAILURE_EMAIL_TO="userRegistrationFailureEmailTo";
 public static final String USER_REG_FAILURE_EMAIL_SUBJECT="userRegistrationFailureEmailSubject";

 /*MainMethodConstants*/
 public static final String GATEWAY_URL ="${gateway.uri}";
 public static final String ACCOUNT_MODELER ="tp-app-account";
 public static final String VERSION_1="v1";
 public static final String ACCOUNT_MODELER_API_VERSION_1="Account Modeler API v1";
 public static final String CURRENT_PROJECT="com.techsophy.tsf.account.*";
 public static final String MULTITENANCY_PROJECT="com.techsophy.multitenancy.mongo.*";
 public static final String EMPTY_STRING = "String Is Empty";
 public static final int SEVEN =7;
 public static final int ONE =1 ;

 //otp service implementation
 public static final String TYPE_SHOULD_NOT_BE_BLANK="type should not be blank";
 public static final String TO_SHOULD_NOT_BE_BLANK="to should not be blank";
 public static final String OTP_SHOULD_NOT_BE_BLANK="otp should not be blank";
 public static final String OTP="otp";
 public static final String NOTIFICATION_ENABLED="notification.enabled";
 public static final String MAIL_MESSAGE="mailMessage";
 public static final String SMS_MESSAGE="smsMessage";
 public static final String OPERATION_NAME="operationName";
 public static final String VARIABLES_STRING ="variables";
 public static final String QUERY_STRING ="query";
 public static final String ERRORS="errors";
 public static final String SEND_EMAIL="SEND_EMAIL";
 public static final String SEND_SMS="SEND_SMS";
 public static final String MOBILE="mobile";
 public static final String EMAIL="email";
 public static final String OTP_BODY="Your OTP - ";
 public static final String OTP_FILLER= "${otp}";
 public static final String NOTIFICATION_URL="/notification/v1/graphql";
 public static final String NOTIFICATION_SMS_QUERY="${notification.sms.query}";
 public static final String NOTIFICATION_EMAIL_QUERY="${notification.email.query}";
 public static final String OTP_VALIDITY="${otp.validity}";
 public static final String TP_OTP_COLLECTION ="tp_otp";
 public static final String OTP_LENGTH="${otp.length}";
 public static final String GENERATING_OTP="generating otp";
 public static final String FIND_CHANNEL_AND_TO="find channel and to";
 public static final String OTP_DEFINITION="otp definition";
 public static final String SET_BODY="set body";
 public static final String SEND_OTP="send otp";
 public static final String INVOKING_EMAIL_SERVICE="invoking email service";

 //MenuConstant
 public static final String MENUS="/menus";
 public static final String MENU="/menu";
 public static final String MENU_TABLE="tp_menu";
 public static final String SAVE_MENU_SUCCESS ="MENU.SAVED.SUCCESS";
 public static final String DELETE_MENU_SUCCESS ="MENU.DELETE.SUCCESS";
 public static final String GET_MENU_SUCCESS ="MENU.RETRIEVE.SUCCESS";
 public static final String GET_MENU_ROLE_SUCCESS="MENU.ROLE.RETRIEVED.SUCCESS";
 public static final String MENU_ID="/{id}";

 //MenuRoleConstants
 public static final String MENU_ROLE_TABLE="tp_menu_role";
 public static final String MENU_ROLE="/menurole";
 public static final String MENU_ROLE_ID="/{id}";
}
