package com.cqucc.hotel.service;

import com.cqucc.hotel.common.BusinessException;
import com.cqucc.hotel.common.PasswordUtil;
import com.cqucc.hotel.dao.BusinessDao;
import com.cqucc.hotel.dao.CrudDao;
import com.cqucc.hotel.entity.Guest;
import com.cqucc.hotel.entity.OperationLog;
import com.cqucc.hotel.entity.Staff;
import com.cqucc.hotel.entity.SysUser;
import com.cqucc.hotel.security.LoginUser;
import com.cqucc.hotel.security.TokenStore;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证与用户服务：登录、客人自助注册、员工账号管理、口令修改、操作日志。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    /** 登录失败锁定策略：5 次失败锁定 15 分钟。 */
    private static final int MAX_FAIL = 5;
    private static final long LOCK_MILLIS = 15 * 60 * 1000L;

    private final CrudDao<SysUser> userDao;
    private final CrudDao<Guest> guestDao;
    private final CrudDao<Staff> staffDao;
    private final CrudDao<OperationLog> logDao;
    private final BusinessDao businessDao;
    private final TokenStore tokenStore;

    private final Map<String, Integer> failCounter = new ConcurrentHashMap<>();
    private final Map<String, Long> lockUntil = new ConcurrentHashMap<>();

    public AuthService(CrudDao<SysUser> sysUserDao, CrudDao<Guest> guestCrudDao, CrudDao<Staff> staffCrudDao,
                       CrudDao<OperationLog> operationLogDao, BusinessDao businessDao, TokenStore tokenStore) {
        this.userDao = sysUserDao;
        this.guestDao = guestCrudDao;
        this.staffDao = staffCrudDao;
        this.logDao = operationLogDao;
        this.businessDao = businessDao;
        this.tokenStore = tokenStore;
    }

    /** 登录结果。 */
    public record LoginResult(String token, LoginUser user, Long guestId, Long staffId) {
    }

    public LoginResult login(String username, String password, HttpServletRequest request) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException("请输入账号和密码");
        }
        String account = username.trim();
        Long locked = lockUntil.get(account);
        if (locked != null && locked > System.currentTimeMillis()) {
            long minutes = (locked - System.currentTimeMillis()) / 60000 + 1;
            throw new BusinessException(429, "账号已锁定，请 " + minutes + " 分钟后重试");
        }
        List<SysUser> users = userDao.findList(CrudDao.cond("username", account));
        if (users.isEmpty()) {
            recordFail(account);
            writeLog(null, account, "认证", "登录", "账号不存在", request, false);
            throw new BusinessException(401, "账号或密码错误");
        }
        SysUser user = users.get(0);
        if (!PasswordUtil.matches(password, user.getSalt(), user.getPasswordHash())) {
            recordFail(account);
            writeLog(user.getId(), account, "认证", "登录", "密码错误", request, false);
            throw new BusinessException(401, "账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        failCounter.remove(account);
        lockUntil.remove(account);

        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRealName(), user.getRole());
        Guest guest = businessDao.findGuestByUserId(user.getId());
        Staff staff = businessDao.findStaffByUserId(user.getId());
        if (guest != null) {
            loginUser.setGuestId(guest.getId());
        }
        if (staff != null) {
            loginUser.setStaffId(staff.getId());
        }
        String token = tokenStore.create(loginUser);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("last_login_at", LocalDateTime.now());
        userDao.updateById(user.getId(), values);
        writeLog(user.getId(), account, "认证", "登录", "登录成功（" + user.getRole() + "）", request, true);
        return new LoginResult(token, loginUser, loginUser.getGuestId(), loginUser.getStaffId());
    }

    /** 客人自助注册：创建登录账号 + 客人档案（自动生成客人编号）。 */
    @Transactional
    public LoginResult register(String username, String password, String realName, String phone, String gender,
                                String idCard, String birthday, HttpServletRequest request) {
        if (username == null || username.trim().length() < 4) {
            throw new BusinessException("账号至少 4 位");
        }
        if (password == null || password.length() < 6) {
            throw new BusinessException("密码至少 6 位");
        }
        if (realName == null || realName.isBlank()) {
            throw new BusinessException("请填写真实姓名");
        }
        if (phone == null || !phone.matches("1[3-9]\\d{9}")) {
            throw new BusinessException("请填写正确的手机号");
        }
        if (!userDao.findList(CrudDao.cond("username", username.trim())).isEmpty()) {
            throw new BusinessException(409, "该账号已被注册");
        }
        if (businessDao.findGuestByPhone(phone) != null) {
            throw new BusinessException(409, "该手机号已注册过客人档案，请直接登录或联系前台");
        }
        SysUser user = new SysUser();
        user.setUsername(username.trim());
        String salt = PasswordUtil.newSalt();
        user.setSalt(salt);
        user.setPasswordHash(PasswordUtil.hash(password, salt));
        user.setRealName(realName.trim());
        user.setRole("GUEST");
        user.setPhone(phone);
        user.setStatus(1);
        Map<String, Object> userValues = new LinkedHashMap<>();
        userValues.put("username", user.getUsername());
        userValues.put("password_hash", user.getPasswordHash());
        userValues.put("salt", user.getSalt());
        userValues.put("real_name", user.getRealName());
        userValues.put("role", user.getRole());
        userValues.put("phone", user.getPhone());
        userValues.put("status", 1);
        Long userId;
        try {
            userId = userDao.insertReturnKey(userValues);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "该账号已被注册");
        }

        Guest guest = new Guest();
        guest.setUserId(userId);
        guest.setGuestNo("G" + LocalDate.now().getYear() + String.format("%04d", userId));
        guest.setRealName(realName.trim());
        guest.setGender(gender == null || gender.isBlank() ? "男" : gender);
        guest.setPhone(phone);
        guest.setIdCard(idCard);
        guest.setIdType("身份证");
        if (birthday != null && !birthday.isBlank()) {
            guest.setBirthday(LocalDate.parse(birthday));
        }
        guest.setMemberLevel("NORMAL");
        guest.setBalanceCents(0);
        guest.setPoints(0);
        guest.setTotalStayCount(0);
        guest.setTotalConsumeCents(0);
        guest.setStatus("NORMAL");
        Long guestId = guestDao.insertReturnKey(Mappers.guest(guest));

        writeLog(userId, user.getUsername(), "认证", "注册", "客人自助注册成功，客人编号 " + guest.getGuestNo(), request, true);

        LoginUser loginUser = new LoginUser(userId, user.getUsername(), user.getRealName(), "GUEST");
        loginUser.setGuestId(guestId);
        String token = tokenStore.create(loginUser);
        return new LoginResult(token, loginUser, guestId, null);
    }

    /** 管理员新增员工账号（前台/客房服务/管理员）。 */
    @Transactional
    public Long createStaff(String username, String password, String realName, String role, String phone,
                            String gender, String position, String workShift, String intro,
                            HttpServletRequest request) {
        if (username == null || username.trim().length() < 4) {
            throw new BusinessException("账号至少 4 位");
        }
        if (!userDao.findList(CrudDao.cond("username", username.trim())).isEmpty()) {
            throw new BusinessException(409, "该账号已存在");
        }
        String defaultPwd = password == null || password.isBlank() ? "123456" : password;
        SysUser user = new SysUser();
        user.setUsername(username.trim());
        String salt = PasswordUtil.newSalt();
        user.setSalt(salt);
        user.setPasswordHash(PasswordUtil.hash(defaultPwd, salt));
        user.setRealName(realName == null ? username : realName.trim());
        user.setRole(role == null ? "FRONTDESK" : role);
        user.setPhone(phone);
        user.setStatus(1);
        Map<String, Object> userValues = new LinkedHashMap<>();
        userValues.put("username", user.getUsername());
        userValues.put("password_hash", user.getPasswordHash());
        userValues.put("salt", user.getSalt());
        userValues.put("real_name", user.getRealName());
        userValues.put("role", user.getRole());
        userValues.put("phone", user.getPhone());
        userValues.put("status", 1);
        Long userId = userDao.insertReturnKey(userValues);

        Staff staff = new Staff();
        staff.setUserId(userId);
        staff.setStaffNo("S" + String.format("%03d", userId));
        staff.setGender(gender == null ? "男" : gender);
        staff.setPosition(position == null ? "前台接待" : position);
        staff.setWorkShift(workShift == null ? "白班" : workShift);
        staff.setIntro(intro);
        staff.setStatus("ON_DUTY");
        staffDao.insertReturnKey(Mappers.staff(staff));

        writeLog(null, username, "员工管理", "新增账号", "新增 " + user.getRole() + " 账号", request, true);
        return userId;
    }

    public SysUser findById(Long userId) {
        return userDao.findById(userId);
    }

    public List<SysUser> listUsers(String role) {
        return userDao.findList(role == null || role.isBlank() ? Map.of() : CrudDao.cond("role", role), "id", null, null);
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userDao.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!PasswordUtil.matches(oldPassword, user.getSalt(), user.getPasswordHash())) {
            throw new BusinessException("原密码不正确");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("新密码至少 6 位");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        String salt = PasswordUtil.newSalt();
        values.put("salt", salt);
        values.put("password_hash", PasswordUtil.hash(newPassword, salt));
        userDao.updateById(userId, values);
    }

    public void resetPassword(Long userId, String newPassword) {
        SysUser user = userDao.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        String salt = PasswordUtil.newSalt();
        values.put("salt", salt);
        values.put("password_hash", PasswordUtil.hash(
                newPassword == null || newPassword.isBlank() ? "123456" : newPassword, salt));
        userDao.updateById(userId, values);
    }

    public void updateStatus(Long userId, Integer status) {
        userDao.updateById(userId, Map.of("status", status));
        log.info("用户 {} 状态更新为 {}", userId, status);
    }

    public void logout(String token, Long userId) {
        tokenStore.remove(token);
        log.info("用户 {} 已退出登录", userId);
    }

    public long onlineCount() {
        return tokenStore.onlineCount();
    }

    /** 记录操作日志（失败不影响主流程）。 */
    public void writeLog(Long userId, String username, String module, String action, String detail,
                         HttpServletRequest request, boolean success) {
        try {
            OperationLog entity = new OperationLog();
            entity.setUserId(userId);
            entity.setUsername(username);
            entity.setModule(module);
            entity.setAction(action);
            entity.setDetail(detail);
            entity.setIp(request == null ? null : clientIp(request));
            entity.setSuccess(success ? 1 : 0);
            logDao.insertReturnKey(Mappers.operationLog(entity));
        } catch (Exception e) {
            log.warn("操作日志写入失败：{}", e.getMessage());
        }
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void recordFail(String account) {
        int count = failCounter.merge(account, 1, Integer::sum);
        if (count >= MAX_FAIL) {
            lockUntil.put(account, System.currentTimeMillis() + LOCK_MILLIS);
            failCounter.remove(account);
            log.warn("账号 {} 连续登录失败，已锁定 15 分钟", account);
        }
    }
}
