package com.maxcar.stock.vo;

import java.io.Serializable;
import java.util.Date;

/**
 * @Author sunyazhou
 * @Date 2018/11/28 15:16
 * @desc
 */
public class CarReviewVo implements Serializable {
    private String id;

    private String carId;

    private String marketId;

    private String userId;

    private String userName;

    private String outReason;

    private Date backTime;

    private Integer isPass;

    private String reasonDesc;

    private Integer isPledge;

    private Date insertTime;

    private Integer isValid;

    private Integer stockStatus;//临时变量 车辆状态

    public Integer getStockStatus() {
        return stockStatus;
    }

    public void setStockStatus(Integer stockStatus) {
        this.stockStatus = stockStatus;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCarId() {
        return carId;
    }

    public void setCarId(String carId) {
        this.carId = carId == null ? null : carId.trim();
    }

    public String getMarketId() {
        return marketId;
    }

    public void setMarketId(String marketId) {
        this.marketId = marketId == null ? null : marketId.trim();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId == null ? null : userId.trim();
    }

    public String getOutReason() {
        return outReason;
    }

    public void setOutReason(String outReason) {
        this.outReason = outReason == null ? null : outReason.trim();
    }

    public Date getBackTime() {
        return backTime;
    }

    public void setBackTime(Date backTime) {
        this.backTime = backTime;
    }

    public Integer getIsPass() {
        return isPass;
    }

    public void setIsPass(Integer isPass) {
        this.isPass = isPass;
    }

    public String getReasonDesc() {
        return reasonDesc;
    }

    public void setReasonDesc(String reasonDesc) {
        this.reasonDesc = reasonDesc == null ? null : reasonDesc.trim();
    }

    public Integer getIsPledge() {
        return isPledge;
    }

    public void setIsPledge(Integer isPledge) {
        this.isPledge = isPledge;
    }

    public Date getInsertTime() {
        return insertTime;
    }

    public void setInsertTime(Date insertTime) {
        this.insertTime = insertTime;
    }

    public Integer getIsValid() {
        return isValid;
    }

    public void setIsValid(Integer isValid) {
        this.isValid = isValid;
    }
}
