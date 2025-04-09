package com.yzy.partner.service;

import com.yzy.partner.model.domain.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.annotation.Resource;

/**
 * Redis 测试
 *
 * @author yzy
 */
@SpringBootTest
public class RedisTest {

    @Resource
    private RedisTemplate redisTemplate;

    @Test
    void test() {
        ValueOperations valueOperations = redisTemplate.opsForValue();
        // 增
        valueOperations.set("yzyString", "dog");
        valueOperations.set("yzyInt", 1);
        valueOperations.set("yzyDouble", 2.0);
        User user = new User();
        user.setId(1L);
        user.setUsername("yzy");
        valueOperations.set("yzyUser", user);
        // 查
        Object yzy = valueOperations.get("yzyString");
        Assertions.assertTrue("dog".equals((String) yzy));
        yzy = valueOperations.get("yzyInt");
        Assertions.assertTrue(1 == (Integer) yzy);
        yzy = valueOperations.get("yzyDouble");
        Assertions.assertTrue(2.0 == (Double) yzy);
        System.out.println(valueOperations.get("yzyUser"));
        valueOperations.set("yzyString", "dog");
        redisTemplate.delete("yzyString");
    }
}
