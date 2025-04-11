package com.yzy.partner.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yzy.partner.common.ErrorCode;
import com.yzy.partner.common.ResultUtils;
import com.yzy.partner.constant.UserConstant;
import com.yzy.partner.exception.BusinessException;
import com.yzy.partner.model.domain.User;
import com.yzy.partner.service.UserService;
import com.yzy.partner.mapper.UserMapper;
import com.yzy.partner.utils.AlgorithmUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.yzy.partner.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户服务实现类
 *
 * @author yzy
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 盐值，混淆密码
     */
    private static final String SALT = "yzy";

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword, String code) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword, code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (code.length() > 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "编号过长");
        }
        // 账户不能包含特殊字符
        String validPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) {
            return -1;
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            return -1;
        }
        // 账户不能重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 编号不能重复
        queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("code", code);
        count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "编号重复");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 3. 插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setCode(code);
        boolean saveResult = this.save(user);
        if (!saveResult) {
            return -1;
        }
        return user.getId();
    }



    @Override
    public User userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            return null;
        }
        if (userAccount.length() < 4) {
            return null;
        }
        if (userPassword.length() < 8) {
            return null;
        }
        // 账户不能包含特殊字符
        String validPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) {
            return null;
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = userMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            return null;
        }
        // 3. 用户脱敏
        User safetyUser = getSafetyUser(user);
        // 4. 记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, safetyUser);
        return safetyUser;
    }

    /**
     * 用户脱敏
     *
     * @param originUser
     * @return
     */
    @Override
    public User getSafetyUser(User originUser) {
        if (originUser == null) {
            return null;
        }
        User safetyUser = new User();
        safetyUser.setId(originUser.getId());
        safetyUser.setUsername(originUser.getUsername());
        safetyUser.setUserAccount(originUser.getUserAccount());
        safetyUser.setAvatarUrl(originUser.getAvatarUrl());
        safetyUser.setGender(originUser.getGender());
        safetyUser.setPhone(originUser.getPhone());
        safetyUser.setEmail(originUser.getEmail());
        safetyUser.setCode(originUser.getCode());
        safetyUser.setUserRole(originUser.getUserRole());
        safetyUser.setUserStatus(originUser.getUserStatus());
        safetyUser.setCreateTime(originUser.getCreateTime());
        safetyUser.setTags(originUser.getTags());
        return safetyUser;
    }

    /**
     * 用户注销
     *
     * @param request
     */
    @Override
    public int userLogout(HttpServletRequest request) {
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return 1;
    }

    /**
     * 根据标签搜索用户（内存过滤）
     *
     * @param tagNameList 用户要拥有的标签
     * @return
     */
    @Override
    public List<User> searchUsersByTags(List<String> tagNameList) {
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 先查询所有用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        List<User> userList = userMapper.selectList(queryWrapper);
        Gson gson = new Gson();
        // 在内存中判断是否包含要求的标签
        return userList.stream().filter(user -> {
            String tagsStr = user.getTags();
            Set<String> tempTagNameSet = gson.fromJson(tagsStr, new TypeToken<Set<String>>() {
            }.getType());
            tempTagNameSet = Optional.ofNullable(tempTagNameSet)
                                     .orElse(new HashSet<>());
            for (String tagName : tagNameList) {
                if (!tempTagNameSet.contains(tagName)) {
                    return false;
                }
            }
            return true;
        }).map(this::getSafetyUser).collect(Collectors.toList());
    }

    @Override
    public int updateUser(User user, User loginUser) {
        long userId = user.getId();
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 权限校验：管理员可以更新任意用户，普通用户只能更新自己
        if (!isAdmin(loginUser) && userId != loginUser.getId()) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        // 检查用户是否存在
        User oldUser = userMapper.selectById(userId);
        if (oldUser == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        //加密密码
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + user.getUserPassword()).getBytes());
        user.setUserPassword(encryptPassword);
        // 执行更新操作
        return userMapper.updateById(user);
    }


    @Override
    public User getLoginUser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        return (User) userObj;
    }

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        // 仅管理员可查询
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        return user != null && user.getUserRole() == UserConstant.ADMIN_ROLE;
    }

    /**
     * 是否为管理员
     *
     * @param loginUser
     * @return
     */
    @Override
    public boolean isAdmin(User loginUser) {
        return loginUser != null && loginUser.getUserRole() == UserConstant.ADMIN_ROLE;
    }


    @Override
    public List<User> matchUsers(long num, User loginUser) {
        //  获取当前用户的标签
        String loginUserTags = loginUser.getTags();
        if (StringUtils.isBlank(loginUserTags)) {
            return Collections.emptyList(); // 如果当前用户没有标签，直接返回空列表
        }

        // 使用 Gson 解析当前用户的标签
        Gson gson = new Gson();
        List<String> loginUserTagList = gson.fromJson(loginUserTags, new TypeToken<List<String>>() {}.getType());

        // 查询所有有标签的用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "tags").isNotNull("tags");
        List<User> allUsers = this.list(queryWrapper);

        // 计算每个用户的相似度（编辑距离）
        List<Pair<User, Long>> userDistancePairs = new ArrayList<>();
        for (User user : allUsers) {
            if (user.getId() == loginUser.getId()) {
                continue; // 跳过当前用户自己
            }

            String userTags = user.getTags();
            if (StringUtils.isBlank(userTags)) {
                continue; // 跳过没有标签的用户
            }

            // 解析用户的标签并计算编辑距离
            List<String> userTagList = gson.fromJson(userTags, new TypeToken<List<String>>() {}.getType());
            long distance = AlgorithmUtils.minDistance(loginUserTagList, userTagList);
            userDistancePairs.add(new Pair<>(user, distance));
        }

        // 按相似度排序并筛选前 num 个用户
        List<Pair<User, Long>> topUserPairs = userDistancePairs.stream()
                .sorted((a, b) -> Long.compare(a.getValue(), b.getValue())) // 按距离从小到大排序
                .limit(num)
                .collect(Collectors.toList());

        // 获取前 num 个用户的 ID 列表
        List<Long> topUserIds = topUserPairs.stream()
                .map(pair -> pair.getKey().getId())
                .collect(Collectors.toList());

        // 根据 ID 查询用户信息并去重
        QueryWrapper<User> idQueryWrapper = new QueryWrapper<>();
        idQueryWrapper.in("id", topUserIds);
        Map<Long, List<User>> userIdToUsersMap = this.list(idQueryWrapper)
                .stream()
                .map(this::getSafetyUser) // 确保用户数据安全
                .collect(Collectors.groupingBy(User::getId));

        //  按照前 num 个用户的 ID 顺序构建最终结果列表
        List<User> finalUsers = new ArrayList<>();
        for (Long userId : topUserIds) {
            List<User> users = userIdToUsersMap.get(userId);
            if (users != null && !users.isEmpty()) {
                finalUsers.add(users.get(0)); // 取第一个匹配的用户
            }
        }

        return finalUsers;
    }

    @Override
    public Page<User> recommendUsers(String redisKey, long pageSize, long pageNum) {
        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
        // 如果有缓存，直接读缓存
        Page<User> userPage = (Page<User>) valueOperations.get(redisKey);
        if (userPage != null) {
            return userPage;
        }
        // 无缓存，查数据库
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        userPage = this.page(new Page<>(pageNum, pageSize), queryWrapper);
        // 写缓存
        try {
            valueOperations.set(redisKey, userPage, 30000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("redis set key error", e);
        }
        return userPage;
    }

    @Override
    public int updateTags(String oldTag, String newTag, String operation,long id) {
        if (id < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID无效");
        }

        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "用户不存在");
        }
        String userTags=user.getTags();
        // 1. 获取当前用户的标签并转换成列表
        Gson gson = new Gson();
        List<String> currentTags = gson.fromJson(userTags,new TypeToken<List<String>>() {}.getType());

        // 2. 根据操作类型处理标签
        switch (operation.toLowerCase()) {
            case "add":
                if (!currentTags.contains(newTag)) {
                    currentTags.add(newTag);  // 添加新标签
                } else {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签已存在");
                }
                break;

            case "remove":
                if (currentTags.contains(oldTag)) {
                    currentTags.remove(oldTag);  // 删除旧标签
                    // 如果标签列表中删除后出现空字符串，移除空字符串
                    currentTags.removeIf(tag -> tag.isEmpty());
                } else {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "未找到指定的标签");
                }
                break;

            case "update":
                if (currentTags.contains(oldTag)) {
                    int index = currentTags.indexOf(oldTag);  // 获取旧标签的索引
                    currentTags.set(index, newTag);  // 替换为新标签
                } else {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "未找到指定的标签");
                }
                break;

            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的操作类型");
        }
        // 3. 更新数据库中的标签字段
        user.setTags(gson.toJson(currentTags));
        return userMapper.updateById(user);  // 更新数据库中的用户信息
    }


}




