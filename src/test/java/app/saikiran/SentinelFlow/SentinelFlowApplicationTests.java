package app.saikiran.SentinelFlow;

import app.saikiran.SentinelFlow.service.RedisCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SentinelFlowApplicationTests {

	@MockBean
	private RedisConnectionFactory redisConnectionFactory;

	@MockBean
	private RedisTemplate<String, Object> redisTemplate;

	@MockBean
	private RedisCacheService redisCacheService;

	@Test
	void contextLoads() {
	}

}
