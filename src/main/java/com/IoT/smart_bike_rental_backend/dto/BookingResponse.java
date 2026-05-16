package com.IoT.smart_bike_rental_backend.dto;

import com.IoT.smart_bike_rental_backend.model.Booking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * BookingResponse - Response DTO for booking operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long bookingId;
    private Long userId;
    private String userName;
    private String userEmail;
    private String bikeId;
    private String qrCode;
    private LocalDateTime bookingTime;
    private String status;
    private String message;

    public static BookingResponse fromBooking(Booking booking) {
        return fromBooking(booking, null);
    }

    public static BookingResponse fromBooking(Booking booking, String message) {
        return BookingResponse.builder()
                .bookingId(booking.getId())
                .userId(booking.getUser() != null ? booking.getUser().getId() : null)
                .userName(booking.getUser() != null ? booking.getUser().getName() : null)
                .userEmail(booking.getUser() != null ? booking.getUser().getEmail() : null)
                .bikeId(booking.getBike() != null ? booking.getBike().getBikeId() : null)
                .qrCode(booking.getBike() != null ? booking.getBike().getQrCode() : null)
                .bookingTime(booking.getBookingTime())
                .status(booking.getStatus())
                .message(message)
                .build();
    }
}
