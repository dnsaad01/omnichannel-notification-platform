package com.eventflow.ingestion.dto;

public class UserPreferenceDto {
    private String userId;
    private String emailAddress;
    private String phoneNumber;
    private Boolean emailEnabled;
    private Boolean smsEnabled;
    private Boolean pushEnabled;

    public UserPreferenceDto() {}

    public UserPreferenceDto(String userId, String emailAddress, String phoneNumber, Boolean emailEnabled, Boolean smsEnabled, Boolean pushEnabled) {
        this.userId = userId;
        this.emailAddress = emailAddress;
        this.phoneNumber = phoneNumber;
        this.emailEnabled = emailEnabled;
        this.smsEnabled = smsEnabled;
        this.pushEnabled = pushEnabled;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public Boolean getEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(Boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    public Boolean getSmsEnabled() { return smsEnabled; }
    public void setSmsEnabled(Boolean smsEnabled) { this.smsEnabled = smsEnabled; }
    public Boolean getPushEnabled() { return pushEnabled; }
    public void setPushEnabled(Boolean pushEnabled) { this.pushEnabled = pushEnabled; }
}
