package cn.edu.cuc.class10;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 禁用静态资源缓存
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(0);

        // 单词图片存储目录（项目根目录下的 word-images/）
        // 禁用缓存，方便开发者替换图片后实时看到效果
        registry.addResourceHandler("/word-images/**")
                .addResourceLocations("file:./word-images/")
                .setCachePeriod(0);
    }
}
