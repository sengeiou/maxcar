package com.maxcar.market.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.maxcar.barrier.pojo.Barrier;
import com.maxcar.barrier.service.BarrierService;
import com.maxcar.base.dao.BaseDao;
import com.maxcar.base.pojo.InterfaceResult;
import com.maxcar.base.service.impl.BaseServiceImpl;
import com.maxcar.base.util.*;
import com.maxcar.base.util.dasouche.Result;
import com.maxcar.base.util.kafka.PostParam;
import com.maxcar.kafka.service.MessageProducerService;
import com.maxcar.market.dao.ParkingFeeDetailMapper;
import com.maxcar.market.dao.ParkingFeeIntegralMapper;
import com.maxcar.market.dao.ParkingFeeMapper;
import com.maxcar.market.model.request.*;
import com.maxcar.market.model.response.AllParkingFeePack;
import com.maxcar.market.model.response.AllParkingFeeResponse;
import com.maxcar.market.model.response.SumByParkingFeeDetailIdResponse;
import com.maxcar.market.pojo.*;
import com.maxcar.market.service.ParkingFeeIntegralService;
import com.maxcar.market.service.ParkingFeeRuleService;
import com.maxcar.market.service.ParkingFeeService;
import com.maxcar.market.service.mapperService.ParkingFeeDetailMapperService;
import com.maxcar.market.utils.ToolUtils;
import com.maxcar.redis.service.RedisService;
import com.maxcar.user.entity.Organizations;
import com.maxcar.user.entity.Staff;
import com.maxcar.user.entity.User;
import com.maxcar.user.service.OrganizationsService;
import com.maxcar.user.service.StaffService;
import com.maxcar.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service("parkingFeeService")
public class ParkingFeeServiceImpl extends BaseServiceImpl<ParkingFee, String> implements ParkingFeeService {

    static Logger logger = LoggerFactory.getLogger(ParkingFeeServiceImpl.class);
    @Autowired
    private ParkingFeeMapper parkingFeeMapper;

    @Autowired
    private ParkingFeeDetailMapperService parkingFeeDetailMapperService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrganizationsService organizationsService;

    @Autowired
    private ParkingFeeDetailMapper parkingFeeDetailMapper;

    @Autowired
    private ParkingFeeIntegralMapper parkingFeeIntegralMapper;

    @Autowired
    private StaffService staffService;

    @Autowired
    private ParkingFeeRuleService parkingFeeRuleService;

    @Autowired
    private ParkingFeeIntegralService parkingFeeIntegralService;

    @Autowired
    private MessageProducerService messageProducerService;

    @Autowired
    private BarrierService barrierService;



    private String REDIS_ZSET_PARKING_FEE = "PARKING_FEE_LIST";

    @Override
    public BaseDao<ParkingFee, String> getBaseMapper() {
        return parkingFeeMapper;
    }

    /**
     * param:
     * describe: 车辆入库
     * create_date:  lxy   2018/8/30  10:36
     **/
    @Override
    public boolean addParkingFeeDetail(AddParkingFeeDetailRequest request) {

        ParkingFeeDetail ParkingFeeDetail = new ParkingFeeDetail();
        ParkingFee parkingFee = parkingFeeMapper.getCurrentNewRecord(request.getBarrierId(), request.getMaxketId());
        ParkingFeeDetail.setId(UuidUtils.generateIdentifier());
        ParkingFeeDetail.setParkingFeeId(null == parkingFee ? "" : parkingFee.getId());
        ParkingFeeDetail.setMarketId(request.getMaxketId());
        ParkingFeeDetail.setUnionId(request.getUnionId());
        ParkingFeeDetail.setCardNo(request.getCardNo());
        ParkingFeeDetail.setBeforeImage(request.getBeforeImage());
        ParkingFeeDetail.setBeforeTime(new Date());
        ParkingFeeDetail.setInsertOperator(request.getOperator());

        return parkingFeeDetailMapperService.insertSelective(ParkingFeeDetail);

    }


    /**
     * param:
     * describe: 车辆出库
     * create_date:  lxy   2018/8/30  10:45
     **/
    @Override
    public boolean updateParkingFeeDetail(ParkingFeeDetail parkingFeeDetail) {
        return parkingFeeDetailMapperService.updateByPrimaryKeySelective(parkingFeeDetail);

    }

    /**
     * param:
     * describe: 更换班次
     * create_date:  lxy   2018/8/28  19:19
     **/
    @Override
    public ParkingFee updateParkingFee(UpdateParkingFeeRequest request) {

        ParkingFee parkingFeeById = parkingFeeMapper.selectByPrimaryKey(request.getParkingFeeId());
        if (null == parkingFeeById) {
            return null;
        }

        // 更新班次的换岗时间
        ParkingFee updateParkingFee = new ParkingFee();
        updateParkingFee.setId(parkingFeeById.getId());
        updateParkingFee.setLeaveTime(new Date());
        updateParkingFee.setUpdateTime(new Date());
        updateParkingFee.setUpdateOperator(request.getBrakeId());

        if (1 <= parkingFeeMapper.updateByPrimaryKeySelective(updateParkingFee)) {
            ParkingFee addParkingFee = new ParkingFee();
            addParkingFee.setId(UuidUtils.generateIdentifier());
            addParkingFee.setMarketId(request.getMarketId());
            addParkingFee.setShift(ToolUtils.gettimeFormart(parkingFeeById.getShift()));
            addParkingFee.setBrakeId(request.getBrakeId());
            addParkingFee.setEmployeesId(request.getEmployeesId());
            addParkingFee.setInsertOperator(request.getBrakeId());

            if (ToolUtils.isOperationSuccess(parkingFeeMapper.insertSelective(addParkingFee))) {
                return addParkingFee;
            }
        }

        return null;
    }

    /**
     * param:
     * describe: 新建班次
     * create_date:  lxy   2018/8/29  10:32
     **/
    @Override
    public ParkingFee addParkingFee(AddParkingFeeRequest request) {

        ParkingFee maxParkingFee = parkingFeeMapper.getMaxParkingFee();

        ParkingFee parkingFee = new ParkingFee();
        parkingFee.setId(UuidUtils.generateIdentifier());
        parkingFee.setMarketId(request.getMarketId());
        parkingFee.setShift(null == maxParkingFee ? ToolUtils.gettimeFormart(null) : ToolUtils.gettimeFormart(maxParkingFee.getShift()));
        parkingFee.setBrakeId(request.getBrakeId());
        parkingFee.setEmployeesId(request.getEmployeesId());
        parkingFee.setInsertOperator(request.getBrakeId());

        if (ToolUtils.isOperationSuccess(parkingFeeMapper.insertSelective(parkingFee))) {
            return parkingFee;
        }

        return null;
    }

    /**
     * param:
     * describe: 查看指定班次收费详情
     * create_date:  lxy   2018/8/29  10:23
     **/
    @Override
    public SumByParkingFeeDetailIdResponse getSumByParkingFeeDetailId(String parkingFeeId) {
        return parkingFeeDetailMapperService.sumByParkingFeeDetailId(null, parkingFeeId);
    }


    /**
     * param:
     * describe: 查看停车收费详情列表
     * create_date:  lxy   2018/8/28  19:00
     **/
    @Override
    public PageInfo<ParkingFeeDetail> allParkingFeeDetail(AllParkingFeeDetailRequest request) {

        PageHelper.startPage(request.getCurPage(), request.getPageSize());
        PageInfo<ParkingFeeDetail> pageInfo = new PageInfo<>(parkingFeeDetailMapperService.allParkingFeeDetail(request));

        if (null != pageInfo.getList() && !pageInfo.getList().isEmpty()) {
            pageInfo.getList().forEach(x->{
                if (null!= x.getBeforeTime() && null!= x.getAfterTime()){
                    Map map = DateUtils.getHMS(x.getAfterTime(), x.getBeforeTime());
                    x.setHms(map.get("hour")+"小时"+map.get("minute")+"分");
                }
            });
        }

        return pageInfo;
    }

    @Override
    public List<ParkingFeeDetail> selectParkingFee(ParkingFeeDetail feeDetail){
        return parkingFeeDetailMapper.selectParkingFee(feeDetail);
    }

    /**
     * 根据停车卡号获取最后一条记录
     * @param parkingFeeDetail
     * @return
     */
    @Override
    public ParkingFeeDetail selectOneByCardNo(ParkingFeeDetail parkingFeeDetail) {
        return parkingFeeDetailMapper.selectOneByCardNo(parkingFeeDetail);
    }

    @Override
    public int updateByPrimaryKeySelective(ParkingFeeDetail detail) {
        return parkingFeeDetailMapper.updateByPrimaryKeySelective(detail);
    }


    @Override
    public ParkingFeeDetail selectParkingFeeDetail(ParkingFeeDetail parkingFeeDetail) {
        ParkingFeeDetail detail = parkingFeeDetailMapperService.getParkingFeeDetailByUnionIdOrCardNo(
                parkingFeeDetail.getUnionId(), parkingFeeDetail.getCardNo());

        return detail;
    }

    @Override
    public ParkingFeeDetail selectInParkingRecord(ParkingFeeDetail detail) {
        return parkingFeeDetailMapper.getRecordByCardNoOrUnionId(detail);
    }

    @Override
    public int updateAfterTime(ParkingFeeDetail detail) {
        return parkingFeeDetailMapper.updateByPrimaryKeySelective(detail);
    }

    @Override
    public ParkingFeeDetail selectDetail(ParkingFeeDetail detail) {
        return parkingFeeDetailMapper.selectDetail(detail);
    }

    @Override
    public InterfaceResult saveInParking(String marketId, String cardNo,String barrierId) throws Exception {
        InterfaceResult result = new InterfaceResult();
        ParkingFeeDetail feeDetail = new ParkingFeeDetail();
        feeDetail.setMarketId(marketId);
        feeDetail.setCardNo(cardNo);
        List<ParkingFeeDetail> details = parkingFeeDetailMapper.selectParkingFee(feeDetail);
        Barrier ba = barrierService.selectByBarrierId(barrierId);
        if (null == details || details.size() == 0){
            ParkingFeeDetail detail = new ParkingFeeDetail();
            detail.setId(UuidUtils.generateIdentifier());
            detail.setBeforeTime(Calendar.getInstance().getTime());
            detail.setCardNo(cardNo);
            detail.setMarketId(marketId);
            detail.setInsertTime(Calendar.getInstance().getTime());
            int code = parkingFeeDetailMapper.insertSelective(detail);
            if (code == 1) {
                //下发欢迎光临
                sendMessage(marketId,ba,-2);
            }
        }else{
            //下发禁止重复入场
            sendMessage(marketId,ba,-3);
        }
        return result;
    }

    @Override
    public InterfaceResult charge(JSONObject params) throws Exception {
        InterfaceResult result = new InterfaceResult();
        if (null != params && !params.isEmpty()) {
            if (!params.containsKey("barrierId")) {
                result.InterfaceResult600("请选择道闸!");
                return result;
            }
            String barrierId = params.getString("barrierId");

            ParkingFeeDetail detail = JSONObject.toJavaObject(params, ParkingFeeDetail.class);

            ParkingFee parkingFee = parkingFeeMapper.getCurrentNewRecord(barrierId, detail.getMarketId());
            detail.setParkingFeeId(null == parkingFee ? "" : parkingFee.getId());
            Barrier ba = barrierService.selectByBarrierId(barrierId);
            int code = parkingFeeDetailMapper.updateByPrimaryKeySelective(detail);
            if (code == 1) {
                sendMessage(detail.getMarketId(),ba,-1);
            } else {
                result.InterfaceResult600("收费失败!");
            }
        }
        return result;
    }

    @Override
    public InterfaceResult getShift(String barrierId, String marketId, String userId, Integer pageNum, Integer pageSize) throws Exception {
        InterfaceResult result = new InterfaceResult();
        JSONObject json = new JSONObject();
        Date date = Calendar.getInstance().getTime();

        //获取当前道闸最后一条记录
        ParkingFee parkingFee = parkingFeeMapper.getCurrentNewRecord(barrierId, marketId);
        if (null != parkingFee) {
            json.put("isFirst", "0");
            //查询离岗时间为null，当前道闸的上岗记录
            ParkingFee parkingRecord = parkingFeeMapper.getCurrentBarrierRecord(barrierId, marketId);
            if (parkingRecord != null) {
                Date leave = Calendar.getInstance().getTime();
                String leaveStr = DateUtils.getLongDateStr(leave);
                String arriveStr = DateUtils.getLongDateStr(parkingRecord.getArrivalTime());
                parkingRecord.setLeaveTime(Calendar.getInstance().getTime());
                json.put("id", parkingRecord.getId());
                json.put("shift", parkingRecord.getShift());
                json.put("leaveTime", leaveStr);
                json.put("arriveDate", arriveStr);
                //计算上岗时长时分秒
                Map map = DateUtils.getHMS(leave, parkingRecord.getArrivalTime());
                json.put("hour", map.get("hour"));
                json.put("minute", map.get("minute"));
                json.put("second", map.get("second"));
                //收费总金额,根据收费班次和市场id查询
                Integer fee = parkingFeeDetailMapper.sumFeeByThisShift(marketId, parkingRecord.getId());
                json.put("totalFee", null == fee ? 0 : fee.intValue());//
                ParkingFeeDetail parkingFeeDetail = new ParkingFeeDetail();
                parkingFeeDetail.setMarketId(marketId);
                parkingFeeDetail.setParkingFeeId(parkingRecord.getId());
                parkingFeeDetail.setCurrentPage(pageNum);
                parkingFeeDetail.setPageSize(pageSize);
                PageHelper.startPage(pageNum, pageSize);
                List<ParkingFeeDetail> parkingFeeDetails = parkingFeeDetailMapper.getThisShiftRecord(parkingFeeDetail);
                for (ParkingFeeDetail parkingFeeDe : parkingFeeDetails) {
                    Map map1 = DateUtils.getHMS(parkingFeeDe.getAfterTime(), parkingFeeDe.getBeforeTime());
                    StringBuilder sb = new StringBuilder();
                    sb.append(map1.get("hour"));
                    sb.append("时");
                    sb.append(map1.get("minute"));
                    sb.append("分");
                    sb.append(map1.get("second"));
                    sb.append("秒");
                    parkingFeeDe.setParkingTime(sb.toString());
                }
                PageInfo pageInfoDetail = new PageInfo(parkingFeeDetails);
                json.put("list", pageInfoDetail);
                result.InterfaceResult200(json);
            }
        } else {
            //查询不到记录,系统首次部署设置为首班
            ParkingFee parkingFee1 = new ParkingFee();
            Date d = Calendar.getInstance().getTime();
            parkingFee1.setId(UuidUtils.generateIdentifier());
            parkingFee1.setBrakeId(barrierId);
            parkingFee1.setMarketId(marketId);
            parkingFee1.setShift(ToolUtils.gettimeFormart(""));
            parkingFee1.setInsertTime(Calendar.getInstance().getTime());
            parkingFee1.setInsertOperator(userId);
            parkingFee1.setLeaveTime(d);
            json = (JSONObject) JSONObject.toJSON(parkingFee1);
            json.put("isFirst", "1");
            json.put("hour", 0);
            json.put("minute", 0);
            json.put("second", 0);
            json.put("totalFee", 0);
            json.put("arriveDate", DateUtils.getLongDateStr(d));
            json.put("leaveTime", "-");
            result.InterfaceResult200(json);

        }
        return result;
    }

    @Override
    public InterfaceResult getAllOrgByBaoAn(String marketId) {
        InterfaceResult result = new InterfaceResult();
        List<Organizations> organizationsList = organizationsService.getOrgByCodeAndMarketId(marketId);
        Staff staff = new Staff();
        staff.setIsValid(1);
        staff.setGroupId(organizationsList.get(0).getOrgId());
        staff.setStaffType(1);
        staff.setMarketId(marketId);
        List<Staff> staffList = staffService.selectByExample(staff);
        result.InterfaceResult200(staffList);
        return result;
    }

    @Override
    public InterfaceResult changeSure(JSONObject params) throws Exception {
        InterfaceResult interfaceResult = new InterfaceResult();
        String shift = "";
        if (null != params && !params.isEmpty()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String barrierId = params.getString("barrierId");
            String marketId = params.getString("marketId");
            Integer isFirst = params.getInteger("isFirst");
            //换班人的id
            String personId = params.getString("personId");
            shift = params.getString("shift");
            String userId = params.getString("userId");
            String leaveTime = params.getString("leaveTime");
            String parkingFeeId = params.getString("parkingFeeId");
            ParkingFee parkingFee = new ParkingFee();
            if (isFirst == 1) {
                parkingFee.setId(parkingFeeId);
                if (StringUtils.equals(leaveTime, "-")) {
                    parkingFee.setArrivalTime(Calendar.getInstance().getTime());
                } else {
                    parkingFee.setArrivalTime(sdf.parse(leaveTime));
                }
                parkingFee.setEmployeesId(personId);
                parkingFee.setShift(shift);
                parkingFee.setMarketId(marketId);
                parkingFee.setBrakeId(barrierId);
                parkingFee.setInsertTime(Calendar.getInstance().getTime());
                parkingFee.setInsertOperator(userId);
                int re = parkingFeeMapper.insertSelective(parkingFee);
                if (re == 1) {
                    interfaceResult.InterfaceResult200(shift);
                } else {
                    interfaceResult.InterfaceResult600("换班失败!");
                }
            } else {
                parkingFee.setId(parkingFeeId);
                parkingFee.setLeaveTime(sdf.parse(leaveTime));
                parkingFee.setUpdateTime(Calendar.getInstance().getTime());
                parkingFee.setUpdateOperator(userId);
                parkingFee.setEmployeesId(personId);
                int code = parkingFeeMapper.updateByPrimaryKeySelective(parkingFee);
                if (code == 1) {
                    //保存成功,新建班次
                    ParkingFee fee = new ParkingFee();
                    fee.setId(UuidUtils.generateIdentifier());
                    fee.setInsertOperator(userId);
                    fee.setArrivalTime(Calendar.getInstance().getTime());
                    fee.setBrakeId(barrierId);
                    fee.setEmployeesId(personId);
                    shift = ToolUtils.gettimeFormart(shift);
                    fee.setShift(ToolUtils.gettimeFormart(shift));
                    fee.setMarketId(marketId);
                    fee.setInsertTime(Calendar.getInstance().getTime());
                    int re = parkingFeeMapper.insertSelective(fee);
                    if (re == 1) {
                        interfaceResult.InterfaceResult200(shift);
                    } else {
                        interfaceResult.InterfaceResult600("换班失败！");
                    }
                } else {
                    interfaceResult.InterfaceResult600("换班失败！");
                }
            }

        } else {
            interfaceResult.InterfaceResult406();
        }
        return interfaceResult;
    }

    @Override
    public InterfaceResult getRecordByCardNo(JSONObject params) throws Exception {
        InterfaceResult result = new InterfaceResult();
        String marketId = params.getString("marketId");
        String cardNo = params.getString("cardNo");
        String barrierId = params.getString("barrierId");
        ParkingFeeDetail parkingFeeDetail = new ParkingFeeDetail();
        parkingFeeDetail.setMarketId(marketId);
        parkingFeeDetail.setCardNo(cardNo);
        Date endDate = Calendar.getInstance().getTime();
        //获取当前卡号最早的一条记录
        ParkingFeeDetail parkingFeeDe = parkingFeeDetailMapper.getRecordByCardNoOrUnionId(parkingFeeDetail);
        if (null != parkingFeeDe) {
            //取规则计算
            BigDecimal totalFee = parkingFeeRuleService.figureOutParkingFee(
                    parkingFeeDe.getBeforeTime(), endDate, marketId, 0);
            parkingFeeDe.setPrice(null == totalFee ? 0 : totalFee.intValue());
            parkingFeeDe.setAfterTime(endDate);
            StringBuilder sb = new StringBuilder();
            Map map1 = DateUtils.getHMS(parkingFeeDe.getAfterTime(), parkingFeeDe.getBeforeTime());
            sb.append(map1.get("hour"));
            sb.append("时");
            sb.append(map1.get("minute"));
            sb.append("分");
            sb.append(map1.get("second"));
            sb.append("秒");
            parkingFeeDe.setParkingTime(sb.toString());
            Barrier ba = barrierService.selectByBarrierId(barrierId);
            int type = parkingFeeDe.getPrice() == 0 ? -1:parkingFeeDe.getPrice();
            sendMessage(marketId,ba,type);
        } else {
            result.InterfaceResult600("未查到该卡入场记录!");
        }
        return result;
    }

    public void sendMessage(String marketId,Barrier ba,int type)throws Exception {
        String topic = messageProducerService.getTopic(marketId);
        PostParam postParam = new PostParam();
        StringBuilder url = new StringBuilder();
        url.append("/barrier/open/");
        url.append(ba.getMqttTopic());
        url.append("/");
        url.append(ba.getBarrierId());
        url.append("/");
        //刷卡0元直接出场
        url.append(type);//出场
        postParam.setUrl(url.toString());
        postParam.setMarket(marketId);
        postParam.setOnlySend(false);
        postParam.setMethod("get");
        postParam.setMessageTime(Constants.dateformat.format(new Date()));
        logger.info("下发开闸信息开始==>{}", JsonTools.toJson(postParam));
        messageProducerService.sendMessage(topic, JsonTools.toJson(postParam), false, 0, Constants.KAFKA_SASS);
    }

    /**
     * param:
     * describe: 查看停车收费列表
     * create_date:  lxy   2018/8/28  14:54
     **/
    @Override
    public AllParkingFeeResponse allParkingFee(AllParkingFeeRequest request) {

        AllParkingFeeResponse response = new AllParkingFeeResponse();
        List<AllParkingFeePack> list = new ArrayList<>();
        //String key = REDIS_ZSET_PARKING_FEE + "_" + request.getMarketId();


/*

        SumByParkingFeeDetailIdResponse sumByParkingFeeDetailIdResponse = parkingFeeDetailMapperService.sumByParkingFeeDetailId(null);

        // 检测key 是否存在
        if (redisService.hasKey(key)) {
            request.setTotal(redisService.getLengthOfZSet(key).intValue());
            request = ToolUtils.getStartRowAndEndRow(request);

            response.setPageNum(request.getCurPage());
            response.setPageSize(request.getPageSize());
            response.setTotal(redisService.getLengthOfZSet(key).intValue());

            Set<AllParkingFeePack> set = redisService.getObjectValueZSet(key, request.getStartRow().longValue(), request.getEndRow().longValue());
            List<AllParkingFeePack> list = new ArrayList<>(set);

            response.setList(list);
            response.setSumByParkingFeeDetailIdResponse(sumByParkingFeeDetailIdResponse);

            return response;
        }*/
        PageHelper.startPage(request.getCurPage(), request.getPageSize());
        PageInfo<ParkingFee> pageInfo = new PageInfo<>(parkingFeeMapper.allParkingFee(request));


        List<ParkingFee> allParkingFee = pageInfo.getList();

        if (null == allParkingFee || allParkingFee.isEmpty()) {
            response.setPageNum(pageInfo.getPageNum());
            response.setPageSize(pageInfo.getPageSize());
            response.setTotal((int) pageInfo.getTotal());
            response.setList(list);
            return response;
        }

        list = getAllParkingFeePackList(request.getMarketId(), allParkingFee);

       /* if (redisService.hasKey(key)) {
            request.setTotal(redisService.getLengthOfZSet(key).intValue());
            request = ToolUtils.getStartRowAndEndRow(request);

            response.setPageNum(request.getCurPage());
            response.setPageSize(request.getPageSize());
            response.setTotal(redisService.getLengthOfZSet(key).intValue());

            Set<AllParkingFeePack> set = redisService.getObjectValueZSet(key, request.getStartRow().longValue(), request.getEndRow().longValue());
            List<AllParkingFeePack> listSet = new ArrayList<>(set);
            response.setList(listSet);
            response.setSumByParkingFeeDetailIdResponse(sumByParkingFeeDetailIdResponse);

            return response;
        }*/


       /* request.setTotal(list.size());
        request = ToolUtils.getStartRowAndEndRow(request);*/

        response.setPageNum(pageInfo.getPageNum());
        response.setPageSize(pageInfo.getPageSize());
        response.setTotal((int) pageInfo.getTotal());
        response.setList(list);
        response.setSumByParkingFeeDetailIdResponse(parkingFeeDetailMapperService.sumByParkingFeeDetailId(request.getMarketId(), null));

        return response;
    }


    /**
     * param:
     * describe: 封转停车收费数据
     * create_date:  lxy   2018/8/28  16:42
     **/
    private List<AllParkingFeePack> getAllParkingFeePackList(String marketId, List<ParkingFee> allParkingFee) {

        //String key = REDIS_ZSET_PARKING_FEE + "_" + marketId;

        List<AllParkingFeePack> list = new ArrayList<>(allParkingFee.size());

        allParkingFee.forEach(x -> {

            AllParkingFeePack allParkingFeePack = new AllParkingFeePack();
            allParkingFeePack.setParkingFeeId(x.getId());
            allParkingFeePack.setMarketId(x.getMarketId());
            allParkingFeePack.setShift(x.getShift());
            allParkingFeePack.setBrakeId(x.getBrakeId());
            allParkingFeePack.setEmployeesId(x.getEmployeesId());
            allParkingFeePack.setArrivalTime(x.getArrivalTime());
            allParkingFeePack.setLeaveTime(x.getLeaveTime());

            // 获取统计信息
            SumByParkingFeeDetailIdResponse sumByParkingFeeDetailIdResponse = parkingFeeDetailMapperService.sumByParkingFeeDetailId(marketId, x.getId());

            allParkingFeePack.setCarCount(null == sumByParkingFeeDetailIdResponse ? 0 : sumByParkingFeeDetailIdResponse.getCarCount());
            allParkingFeePack.setPayCash(null == sumByParkingFeeDetailIdResponse ? 0 : sumByParkingFeeDetailIdResponse.getPayCash());
            allParkingFeePack.setPayOnline(null == sumByParkingFeeDetailIdResponse ? 0 : sumByParkingFeeDetailIdResponse.getPayOnline());
            allParkingFeePack.setPayParkingVolume(null == sumByParkingFeeDetailIdResponse ? 0 : sumByParkingFeeDetailIdResponse.getParkingVolume());
            allParkingFeePack.setPaySum(null == sumByParkingFeeDetailIdResponse ? 0 : sumByParkingFeeDetailIdResponse.getPaySum());
            allParkingFeePack.setPayWeixin(null == sumByParkingFeeDetailIdResponse ? 0 : sumByParkingFeeDetailIdResponse.getPayWeixin());
            // 获取收费员信息
            User user = userService.selectByPrimaryKey(x.getEmployeesId());
            allParkingFeePack.setEmployeesName(null == user ? "查无此人" : user.getTrueName());

            // 获取道闸信息
            allParkingFeePack.setBrakeName("查无此道闸");

            try {
                Barrier ba = barrierService.selectByBarrierId(allParkingFeePack.getBrakeId());
                allParkingFeePack.setBrakeName((null != ba && null != ba.getBarrierPosition()) ? ba.getBarrierPosition() : "查无此道闸");
            } catch (Exception e) {
                logger.debug(e.getMessage());
            }

            list.add(allParkingFeePack);

           /* if (!redisService.addObjectValueZSet(key, allParkingFeePack, Double.valueOf(x.getShift()))) {
                redisService.del(key);
            }*/

        });

        return list;
    }


    /**
     * 获取停车收费详情信息
     * 一次车辆从入场到出场的收费一共分为四种情况 通过type来区分四种情况
     * 1.停车时长未超出免费停车时间
     * 2.停车时长超出免费停车时间
     * 3.已缴费未超出免费停留时间
     * 4.已缴费超出免费停留时间
     * @param cardNo
     * @return
     */
    @Override
    public InterfaceResult getParkingFeeDetail(String cardNo) throws Exception {
        InterfaceResult interfaceResult = new InterfaceResult();
        Date currDate = new Date();// 定义当前时间
        double totalPay = 0;// 应该支付的总金额
        double reduction = 0;// 会员优惠的金额
        double pay = 0;// 应该支付的金额
        double totalPayed = 0;//二次收费时  已经收取的费用
        int integral = 0;// 积分使用情况
        ParkingFeeDetail detail = new ParkingFeeDetail();
        detail.setCardNo(cardNo);
        ParkingFeeDetail parkingFeeDetail = this.selectOneByCardNo(detail);
        // 判断出场时间是否在四个小时以内  如果在直接提示可以出场
        if (parkingFeeDetail != null){

           if (parkingFeeDetail.getIsVip() == 0){
               // 将此条停车记录标记为会员停车记录
               parkingFeeDetail.setIsVip(1);
               this.updateByPrimaryKeySelective(parkingFeeDetail);
           }
            // 判断支付时间是否为null,不为null为二次出场收费的情况
            if (parkingFeeDetail.getPayTime() == null){
                totalPay = parkingFeeRuleService.figureOutParkingFee(parkingFeeDetail.getBeforeTime(), new Date(), "007", 2).doubleValue();
                JSONObject jsonObject = assemblyParameter(parkingFeeDetail);
                if (totalPay< 20){
                    reduction = totalPay;
                }else {
                    reduction = 20;
                }
                pay = totalPay-reduction;
                jsonObject.put("totalPay",totalPay);// 第一次支付时按照规则计算的总停车费用
                jsonObject.put("reduction",reduction);// 会员减免金额
                jsonObject.put("pay",pay);// 需要缴纳的金额
                if (pay <= 0){
                    jsonObject.put("type",1);
                }else {
                    jsonObject.put("type",2);
                    jsonObject.put("reBilling",getCurrDateParkingFee(parkingFeeDetail.getBeforeTime(),currDate,"007"));
                }
                interfaceResult.InterfaceResult200(jsonObject);
                return interfaceResult;
            }else {
                // 二次出场,判断支付成功所修改的出场时间距离当前时间是否在15分钟以内
                long time = parkingFeeDetail.getPayTime().getTime();// 支付时间
                if (System.currentTimeMillis() <= time +Constants.freeTime){
                    // 剩余免费停留时间
                    long freeTime =15 - DatePoor.getDatePoorMin(new Date(),parkingFeeDetail.getPayTime());
                    JSONObject jsonObject = assemblyParameter(parkingFeeDetail);
                    jsonObject.put("freeTime",freeTime);// 剩余免费停留时间
                    jsonObject.put("payTime",DatePoor.getStringForDateByFormat(parkingFeeDetail.getPayTime(),"yyyy-MM-dd HH:mm:ss"));// 上次支付时间
                    jsonObject.put("type",3);
                    interfaceResult.InterfaceResult200(jsonObject);
                    return interfaceResult;
                }

                JSONObject jsonObject = assemblyParameter(parkingFeeDetail);
                //二次收费时 第二阶段所产生费用
                totalPay = parkingFeeRuleService.figureOutParkingFee(parkingFeeDetail.getBeforeTime(), parkingFeeDetail.getPayTime(), "007", 2,2).doubleValue();
                //二次收费时 已经收取的费用
                totalPayed = parkingFeeRuleService.figureOutParkingFee(parkingFeeDetail.getBeforeTime(), parkingFeeDetail.getPayTime(), "007", 2).doubleValue();
                //二次缴费时 使用的积分记录信息
                ParkingFeeIntegral feeIntegral = parkingFeeIntegralService.selectIntegralByDetailId(parkingFeeDetail.getId());
                    if (null != feeIntegral){
                    integral = feeIntegral.getIntegral();
                }
                // 应该缴纳的费用
                pay = totalPay;
                jsonObject.put("totalPay",totalPay+totalPayed);
                jsonObject.put("totalPayed",totalPayed);
                jsonObject.put("pay",pay);
                jsonObject.put("integral",integral);
                jsonObject.put("reduction",0);
                jsonObject.put("type",4);
                interfaceResult.InterfaceResult200(jsonObject);
                return interfaceResult;
            }
        }else {
            interfaceResult.InterfaceResult600("该车辆还没有入场记录");
        }
        return interfaceResult;
    }

    /**
     * 获取当前时间每小时收费的金额
     * @param beforeDate 入场时间
     * @param currDate 当前时间
     * @param marketId 市场id
     * @return
     */
    @Override
    public int getCurrDateParkingFee(Date beforeDate,Date currDate,String marketId){
        int fee = 0;
        ParkingFeeTotal rule = parkingFeeRuleService.getParkingFeeRuleByMarketId(marketId);
        if (rule != null){
            long time =currDate.getTime() - beforeDate.getTime();
            long time1  = rule.getBeyondTime().longValue()*60*60*1000;
            if (time >=time1){
                fee = rule.getBeyondFee();
            }else {
                fee = 5;
            }
        }
        return fee;
    }

    /**
     *支付成功之后更新会员积分表和停车收费详情记录
     * @param parkingFeeIntegral
     * @return
     */
    @Override
    public InterfaceResult updateParkingFeeIntegralAndDetail(ParkingFeeIntegral parkingFeeIntegral) throws Exception {
        InterfaceResult interfaceResult = new InterfaceResult();
        ParkingFeeDetail detail = new ParkingFeeDetail();
        detail.setCardNo(parkingFeeIntegral.getCardNo());
        detail.setIsVip(1);
        // 判断该卡号是否存在
        ParkingFeeDetail parkingFeeDetail = this.selectOneByCardNo(detail);
        if (parkingFeeDetail != null){
            parkingFeeDetail.setPayTime(DatePoor.getDateForString(parkingFeeIntegral.getPayTime()));
            parkingFeeDetail.setPayType(3);// 公众号收费
            if (null != parkingFeeDetail.getPrice()){
                parkingFeeDetail.setPrice(parkingFeeIntegral.getPrice()+parkingFeeDetail.getPrice());
                parkingFeeDetail.setChargePrice(parkingFeeIntegral.getPrice()+parkingFeeDetail.getChargePrice());
            }else {
                parkingFeeDetail.setPrice(parkingFeeIntegral.getPrice());
                parkingFeeDetail.setChargePrice(parkingFeeIntegral.getPrice());
            }
            this.updateByPrimaryKeySelective(parkingFeeDetail);// 更新停车详情记录
        }
        parkingFeeIntegral.setParkingFeeDetailId(parkingFeeDetail.getId());
        // 查询积分表中是否存在该详情的记录
        ParkingFeeIntegralExample integralExample = new ParkingFeeIntegralExample();
        ParkingFeeIntegralExample.Criteria criteria = integralExample.createCriteria();
        if (StringUtil.isNotEmpty(parkingFeeIntegral.getCardNo())){
            criteria.andCardNoEqualTo(parkingFeeIntegral.getCardNo());
        }
        if (StringUtil.isNotEmpty(parkingFeeIntegral.getParkingFeeDetailId())){
            criteria.andParkingFeeDetailIdEqualTo(parkingFeeIntegral.getParkingFeeDetailId());
        }
        if (StringUtil.isNotEmpty(parkingFeeIntegral.getOpenid())){
            criteria.andOpenidEqualTo(parkingFeeIntegral.getOpenid());
        }
        List<ParkingFeeIntegral> parkingFeeIntegrals = parkingFeeIntegralMapper.selectByExample(integralExample);
        if (parkingFeeIntegrals.size() > 0 && parkingFeeIntegrals.size() == 1){
            // 更改本次停车信息使用的积分
            ParkingFeeIntegral integral = parkingFeeIntegrals.get(0);
            integral.setIntegral(integral.getIntegral()+parkingFeeIntegral.getIntegral());// 跟新积分
            integral.setPrice(integral.getPrice()+parkingFeeIntegral.getPrice());//更新实际付款金额
            integral.setUpdateTime(new Date());
            parkingFeeIntegralMapper.updateByExampleSelective(integral,integralExample);
        }else {
            parkingFeeIntegral.setParkingFeeDetailId(parkingFeeDetail.getId());
            parkingFeeIntegral.setId(UuidUtils.gettimeFormartyyyyMMddHHmmss());
            parkingFeeIntegral.setUpdateTime(new Date());
            parkingFeeIntegralService.insertSelective(parkingFeeIntegral);
        }

        interfaceResult.InterfaceResult200("success");
        return interfaceResult;
    }

    /**
     * 封装入场时间和停车时长
     * @param detail
     * @return
     */
    private JSONObject assemblyParameter(ParkingFeeDetail detail){
        JSONObject params = new JSONObject();
        // 入场时间
        params.put("intimeStr",DatePoor.getStringForDateByFormat(detail.getBeforeTime(),"yyyy-MM-dd HH:mm:ss"));
        // 停车时长
        params.put("parktime",DatePoor.getDatePoor(new Date(),detail.getBeforeTime()));
        return params;
    }

    @Override
    public InterfaceResult testPrice(JSONObject params) throws Exception{
        InterfaceResult result = new InterfaceResult();
        Date begin = DateUtils.LONG_DATE_FORMAT.parse(params.getString("begin"));
        Date end = DateUtils.LONG_DATE_FORMAT.parse(params.getString("end"));
        String marketId = params.getString("marketId");
        Integer carType = params.getInteger("carType");
        BigDecimal money = parkingFeeRuleService.figureOutParkingFee(begin,end,marketId,carType);
        result.setCode("0");
        result.setMsg("SUCCESS");
        result.setData(money.intValue());
        return result;
    }


}
