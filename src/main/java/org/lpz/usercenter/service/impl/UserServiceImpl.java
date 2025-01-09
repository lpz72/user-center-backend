package org.lpz.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.lpz.usercenter.common.ErrorCode;
import org.lpz.usercenter.exception.BusinessException;
import org.lpz.usercenter.model.domain.User;
import org.lpz.usercenter.service.UserService;
import org.lpz.usercenter.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lpz.usercenter.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户服务实现类
 * @author lpz
 */
@Service
@Slf4j //记录日志
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService {

    @Resource
    private UserMapper userMapper;

    /**
     * 盐值，混淆密码
     */
    private static final String salt = "lpz";


    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword,String planetCode) {
        //1.校验
        if (StringUtils.isAnyBlank(userAccount,userPassword,checkPassword,planetCode)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数为空");
        }

        //星球编号长度在1 - 5
        if (planetCode.length() > 5){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"星球编号过长");
        }

        //账户长度不小于4位
        if (userAccount.length() < 4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账户长度过短");
        }
        //密码长度不小于8位
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码长度过短");
        }
        //账户不能包含特殊字符
        String validPattern = "[^a-zA-Z0-9_]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账户包含特殊字符");
        }
        //密码和校验密码相同
        if (!userPassword.equals(checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"两次密码不一致");
        }
        //账户不能重复  涉及到查询数据库，可以把该条判断放到最后，防止资源浪费，避免并不必要的查询
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount",userAccount);//设置查询条件
        long count = userMapper.selectCount(queryWrapper);
        if (count > 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账户重复");
        }

        //星球编号不能重复
        queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("planetCode",planetCode);//设置查询条件
         count = userMapper.selectCount(queryWrapper);
        if (count > 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"星球编号重复");
        }

        //2.加密
        String encryptPassword = DigestUtils.md5DigestAsHex((salt + userPassword).getBytes());

        //3.插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword); //保存加密后的密码
        user.setPlanetCode(planetCode);
        int result = userMapper.insert(user);
        if (result != 1){
            throw new BusinessException(ErrorCode.INSERT_ERROR);
        }
        return user.getId();
    }

    @Override
    public User userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        //1.校验
        if (StringUtils.isAnyBlank(userAccount,userPassword)){
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        //账户长度不小于4位
        if (userAccount.length() < 4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账户长度过短");
        }
        //密码长度不小于8位
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码长度过短");
        }
        //账户不能包含特殊字符
        String validPattern = "[^a-zA-Z0-9_]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账户包含特殊字符");
        }

        //2.加密
        String encryptPassword = DigestUtils.md5DigestAsHex((salt + userPassword).getBytes());

        //查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount",userAccount);//设置查询条件
        queryWrapper.eq("userPassword",encryptPassword);
        User user = userMapper.selectOne(queryWrapper);
        //用户不存在
        if (user == null){
            log.info("user login failed,userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.NULL_USER,"用户不存在");
        }

        //3.用户脱敏
        User saftyUser = getSavetyUser(user);

        //4.记录用户登录态  Attribute相当于一个map
        request.getSession().setAttribute(USER_LOGIN_STATE,saftyUser);
        return saftyUser;
    }


    /**
     * 用户脱敏
     * @param user
     * @return
     */
    @Override
    public User getSavetyUser(User user){
        if (user == null){
            throw new BusinessException(ErrorCode.NULL_USER,"用户不存在");
        }
        User saftyUser = new User();
        saftyUser.setId(user.getId());
        saftyUser.setUsername(user.getUsername());
        saftyUser.setUserAccount(user.getUserAccount());
        saftyUser.setAvatarUrl(user.getAvatarUrl());
        saftyUser.setGender(user.getGender());
        saftyUser.setPhone(user.getPhone());
        saftyUser.setEmail(user.getEmail());
        saftyUser.setUserRole(user.getUserRole());
        saftyUser.setPlanetCode(user.getPlanetCode());
        saftyUser.setUserStatus(user.getUserStatus());
        saftyUser.setCreateTime(user.getCreateTime());

        return saftyUser;
    }

    /**
     * 用户注销
     * @param request
     */
    @Override
    public int userLogout(HttpServletRequest request) {
        //用户退出登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return 1;
    }
}




