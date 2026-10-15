package dev.bedcast.api;
public final class ApiResponse {
    public final boolean success;
    public final long timestamp=System.currentTimeMillis();
    public final Object data;
    public final String error,message;
    private ApiResponse(boolean success,Object data,String error,String message){this.success=success;this.data=data;this.error=error;this.message=message;}
    public static ApiResponse ok(Object data){return new ApiResponse(true,data,null,null);}
    public static ApiResponse error(String error,String message,Object data){return new ApiResponse(false,data,error,message);}
}
