package com.odin.notification.constants;

public class ApplicationConstants {

	public static final String API_VERSION = "/v1";
	public static final String SAVE = "/save";
	public static final String FCM_NOTIFICATION_TOKEN = "/fcmNotificationToken";
	public static final String SEND = "/send";
	public static final String EMAIL = "/email";
	public static final String REST = "/rest";

	// ===== Kafka Topics =====
	public static final String KAFKA_OTP_NOTIFICATION_TOPIC = "otp.notification.message";
	public static final String KAFKA_ALERT_NOTIFICATION_TOPIC = "alert.notification.message";
	public static final String KAFKA_REMINDER_NOTIFICATION_TOPIC = "reminder.notification.message";
	public static final String KAFKA_UNDELIVERED_NOTIFICATION_TOPIC = "undelivered.notification.message";
	public static final String KAFKA_STATUS_UPDATE_NOTIFICATION_TOPIC = "status.update.notification.message";

	// ===== Notification Type/ID Constants =====
	public static final Long NOTIFICATION_ID_DIRECT_MESSAGE = 1L;

	// ===== FCM Configuration Constants =====
	public static final String FCM_NOTIFICATION_CHANNEL_ID = "odin_messenger_channel";
	public static final String FCM_CLICK_ACTION = "FLUTTER_NOTIFICATION_CLICK";
	public static final String FCM_CLICK_ACTION_NONE = "none";
	public static final String FCM_EXTRA_INFO_KEY = "extra_info";
	public static final String FCM_RECEIVER_MOBILE_KEY = "receiverMobile";
	public static final String FCM_SENDER_MOBILE_KEY = "senderMobile";
	public static final String FCM_SENDER_CUSTOMER_ID_KEY = "senderCustomerId";
	public static final String FCM_MESSAGE_KEY = "message";
	public static final String FCM_CONVERSATION_ID_KEY = "conversationId";
	public static final String FCM_FILE_IDS_KEY = "fileIds";
	public static final String FCM_CREDENTIAL_TYPE = "service_account";
	public static final String FCM_NOTIFICATION_TYPE_CHAT = "CHAT_MESSAGE";
	public static final String FCM_NOTIFICATION_TYPE_MESSAGE = "MESSAGE";
	public static final String FCM_NOTIFICATION_TYPE_STATUS = "STATUS_UPDATE";
	public static final String FCM_NOTIFICATION_SOUND_DEFAULT = "default";
	public static final String FCM_NOTIFICATION_SOUND_NONE = "none";

	// ===== Log Messages =====
	public static final String LOG_UNDELIVERED_NOTIFICATION_RECEIVED = "Received undelivered notification with customerId: {}, notificationId: {}, channel: {}";
	public static final String LOG_PROCESSING_NOTIFICATION = "Processing notification for customerId: {}, notificationId: {} with channel: {}";
	public static final String LOG_NOTIFICATION_ID_IS_DIRECT = "Notification ID is {}, using direct message from map without fetching from database";
	public static final String LOG_FCM_TOKEN_FETCHING_FROM_DB = "Fetching FCM token from database for customerId: {}";
	public static final String LOG_FCM_TOKEN_FOUND_IN_DB = "FCM token found in database for customerId: {}";
	public static final String LOG_FCM_TOKEN_RETRIEVED_SUCCESS = "Successfully retrieved FCM token from database for customerId: {}";
	public static final String LOG_FCM_TOKEN_EMPTY_IN_DB = "FCM token is empty or null in database for customerId: {}";
	public static final String LOG_FCM_TOKEN_RECORD_NOT_FOUND = "No NotificationToken record found in database for customerId: {}";
	public static final String LOG_FCM_TOKEN_DB_FETCH_ERROR = "Error fetching FCM token from database for customerId: {}. Error: {}";
	public static final String LOG_FCM_TOKEN_NOT_FOUND = "FCM token not found for customerId: {}. Unable to send push notification.";
	public static final String LOG_SENDING_FCM_PUSH_NOTIFICATION = "Sending FCM push notification to token: {} with message: {}";
	public static final String LOG_FCM_PUSH_SENT_SUCCESSFULLY = "FCM push notification sent successfully. Message ID: {}";
	public static final String LOG_FCM_PUSH_FAILED = "Failed to send FCM push notification. Error: {}";
	public static final String LOG_UNSUPPORTED_CHANNEL = "Unsupported notification channel: {}";
	public static final String LOG_NOTIFICATION_PROCESSING_STARTED = "=== Notification Processing Started ===";
	public static final String LOG_NOTIFICATION_PROCESSING_COMPLETED = "=== Notification Processing Completed ===";
	public static final String LOG_ERROR_PROCESSING_NOTIFICATION = "Error processing notification for customerId: {}. Error: {}";
	
	// ===== Status Update Notification Log Messages =====
	public static final String LOG_STATUS_UPDATE_NOTIFICATION_RECEIVED = "Received status update notification with customerId: {}, notificationId: {}, channel: {}";
	public static final String LOG_STATUS_UPDATE_PROCESSING_STARTED = "=== Status Update Processing Started ===";
	public static final String LOG_STATUS_UPDATE_PROCESSING_COMPLETED = "=== Status Update Processing Completed ===";
	public static final String LOG_STATUS_UPDATE_SENDER_INFO = "Status update from sender: customerId={}, mobile={}";
	public static final String LOG_SENDING_STATUS_UPDATE_NOTIFICATION = "Sending status update notification to token: {} with file count: {}";
	public static final String LOG_STATUS_UPDATE_SENT_SUCCESSFULLY = "Status update notification sent successfully. Message ID: {}";
	public static final String LOG_STATUS_UPDATE_FAILED = "Failed to send status update notification. Error: {}";

}




