package rikka.shizuku;

/** Shizuku API 协议常量（与服务端 Shizuku App 保持一致） */
public class ShizukuApiConstants {

    public static final int SERVER_VERSION = 3;

    public static final String ATTACH_APPLICATION_API_VERSION = "api_version";
    public static final String ATTACH_APPLICATION_PACKAGE_NAME = "package_name";

    public static final String BIND_APPLICATION_PERMISSION_GRANTED = "permission_granted";
    public static final String BIND_APPLICATION_SERVER_PATCH_VERSION = "server_patch_version";
    public static final String BIND_APPLICATION_SERVER_SECONTEXT = "server_secontext";
    public static final String BIND_APPLICATION_SERVER_UID = "server_uid";
    public static final String BIND_APPLICATION_SERVER_VERSION = "server_version";
    public static final String BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE = "should_show_request_permission_rationale";

    public static final String REQUEST_PERMISSION_REPLY_ALLOWED = "allowed";

    /** ShizukuBinderWrapper 转发 transact 用 */
    public static final int BINDER_TRANSACTION_transact = 2;
    public static final String BINDER_DESCRIPTOR = "moe.shizuku.server.IShizukuService";

    /** UserService 参数 */
    public static final String USER_SERVICE_ARG_COMPONENT = "component";
    public static final String USER_SERVICE_ARG_DEBUGGABLE = "debuggable";
    public static final String USER_SERVICE_ARG_VERSION_CODE = "version_code";
    public static final String USER_SERVICE_ARG_DAEMON = "daemon";
    public static final String USER_SERVICE_ARG_USE_32_BIT_APP_PROCESS = "use_32bit_app_process";
    public static final String USER_SERVICE_ARG_PROCESS_NAME = "process_name";
    public static final String USER_SERVICE_ARG_TAG = "tag";
    public static final String USER_SERVICE_ARG_REMOVE = "remove";
    public static final String USER_SERVICE_ARG_NO_CREATE = "no_create";
}
