package com.IoT.smart_bike_rental_backend.dto;
import java.time.LocalDateTime;
public class ApiError {
    private boolean success; private String message; private String error;
    private int status; private LocalDateTime timestamp; private String path;
    public ApiError() { this.success=false; this.timestamp=LocalDateTime.now(); }
    public ApiError(String message,String error,int status){this.success=false;this.message=message;this.error=error;this.status=status;this.timestamp=LocalDateTime.now();}
    public boolean isSuccess(){return success;} public void setSuccess(boolean s){this.success=s;}
    public String getMessage(){return message;} public void setMessage(String m){this.message=m;}
    public String getError(){return error;} public void setError(String e){this.error=e;}
    public int getStatus(){return status;} public void setStatus(int s){this.status=s;}
    public LocalDateTime getTimestamp(){return timestamp;} public void setTimestamp(LocalDateTime t){this.timestamp=t;}
    public String getPath(){return path;} public void setPath(String p){this.path=p;}
}
