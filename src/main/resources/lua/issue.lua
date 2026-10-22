-- 발급 원자 처리. Redis 단일 스레드 + 스크립트 단위 atomic 으로
-- 재고 차감 + 사용자 추가 를 한 덩어리에 묶는다.
--
-- KEYS: stock, users (set), sold_out
-- ARGV: userId, soldOutTtlSeconds
-- 반환: 1=성공, 0=매진, -1=중복 발급

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
  return -1
end

local remaining = tonumber(redis.call('GET', KEYS[1]) or '0')
if remaining <= 0 then
  return 0
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])

if remaining == 1 then
  redis.call('SET', KEYS[3], '1', 'EX', ARGV[2])
end

return 1
