package cn.edu.cuc.class10.repository;

import cn.edu.cuc.class10.entity.TestSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestSessionRepository extends JpaRepository<TestSession, String> {
    
    /**
     * 查询某个用户的所有测试记录（按时间倒序）
     */
    List<TestSession> findByUserIdOrderByEndTimeDesc(String userId);
    
    /**
     * 查询某个用户最近的N条测试记录
     */
    List<TestSession> findTop10ByUserIdOrderByEndTimeDesc(String userId);
    
    /**
     * 查询某个用户在指定时间范围内的测试记录
     */
    List<TestSession> findByUserIdAndEndTimeBetween(String userId, Long startTime, Long endTime);
}
