package cn.edu.cuc.class10.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;

/**
 * Flask AI 服务进程管理器
 *
 * 当 Spring Boot 启动时，自动启动 Flask AI 服务（Python 子进程）；
 * 当 Spring Boot 停止时，自动关闭 Flask 进程。
 *
 * 通过配置项 flask.auto-start=false 可关闭此行为（例如在生产环境使用独立部署的 Flask）。
 */
@Component
@ConditionalOnProperty(name = "flask.auto-start", havingValue = "true", matchIfMissing = true)
public class FlaskProcessManager {

    private static final Logger log = LoggerFactory.getLogger(FlaskProcessManager.class);

    private Process flaskProcess;

    @Value("${flask.base-url:http://localhost:5000}")
    private String flaskBaseUrl;

    @Value("${flask.start-timeout:30000}")
    private long startTimeout;

    /** 项目根目录下 Flask 源码目录 */
    private static final String FLASK_DIR = "flask-service";
    private static final String FLASK_ENTRY = "app.py";
    /** 子进程 stdout/stderr 输出日志 */
    private static final String FLASK_LOG = "flask-service/flask-auto.log";

    @PostConstruct
    public void startFlask() {
        // 先检查是否已有 Flask 在运行（如用户手动启动的）
        if (isFlaskRunning()) {
            log.info("Flask AI 服务已在运行（{}），跳过自动启动", flaskBaseUrl);
            return;
        }

        log.info("正在启动 Flask AI 服务（{}/{}）...", FLASK_DIR, FLASK_ENTRY);

        try {
            ProcessBuilder pb = new ProcessBuilder("python", FLASK_ENTRY);
            pb.directory(new File(FLASK_DIR));
            pb.redirectErrorStream(true);
            pb.redirectOutput(new File(FLASK_LOG));

            flaskProcess = pb.start();
            log.info("Flask 进程已启动（PID: {}），等待就绪...", flaskProcess.pid());

            // 轮询等待 Flask 就绪
            long deadline = System.currentTimeMillis() + startTimeout;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(500);
                if (isFlaskRunning()) {
                    log.info("Flask AI 服务启动成功: {}", flaskBaseUrl);
                    return;
                }
            }

            log.warn("Flask AI 服务未在 {}ms 内就绪（可能模型加载较慢），"
                    + "Spring Boot 将继续启动，请查看日志: {}", startTimeout, FLASK_LOG);
        } catch (IOException e) {
            log.error("启动 Flask AI 服务失败: {}（工作目录: {}）",
                    e.getMessage(), new File(FLASK_DIR).getAbsolutePath());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Flask 就绪检测被中断");
        }
    }

    @PreDestroy
    public void stopFlask() {
        if (flaskProcess != null && flaskProcess.isAlive()) {
            log.info("正在关闭 Flask AI 服务（PID: {}）...", flaskProcess.pid());
            flaskProcess.destroy();
            try {
                boolean stopped = flaskProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                if (!stopped) {
                    flaskProcess.destroyForcibly();
                    log.warn("Flask 进程未在 10s 内正常退出，已强制终止");
                } else {
                    log.info("Flask AI 服务已正常关闭");
                }
            } catch (InterruptedException e) {
                flaskProcess.destroyForcibly();
                Thread.currentThread().interrupt();
                log.warn("Flask 关闭等待被中断，已强制终止");
            }
        }
    }

    /**
     * 通过调用健康检查接口判断 Flask 是否运行正常
     */
    private boolean isFlaskRunning() {
        try {
            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> resp = rest.getForEntity(
                    flaskBaseUrl + "/api/health", String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
