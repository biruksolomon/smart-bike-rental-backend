package com.IoT.smart_bike_rental_backend.dto;
import java.time.LocalDateTime;
public class ApiResponse<T> {
    private boolean success; private String message; private T data; private LocalDateTime timestamp;
    public ApiResponse(){this.timestamp=LocalDateTime.now();}
    public ApiResponse(boolean s,String m,T d){this.success=s;this.message=m;this.data=d;this.timestamp=LocalDateTime.now();}
    public ApiResponse(boolean s,String m){this.success=s;this.message=m;this.timestamp=LocalDateTime.now();}
    public boolean isSuccess(){return success;} public void setSuccess(boolean s){this.success=s;}
    public String getMessage(){return message;} public void setMessage(String m){this.message=m;}
    public T getData(){return data;} public void setData(T d){this.data=d;}
    public LocalDateTime getTimestamp(){return timestamp;} public void setTimestamp(LocalDateTime t){this.timestamp=t;}
    public static <T> ApiResponse<T> success(String m,T d){return new ApiResponse<>(true,m,d);}
    public static <T> ApiResponse<T> success(String m){return new ApiResponse<>(true,m);}
    public static <T> ApiResponse<T> error(String m){return new ApiResponse<>(false,m);}
    public static <T> ApiResponse<T> error(String m,T d){return new ApiResponse<>(false,m,d);}
}
