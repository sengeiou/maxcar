package com.maxcar;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.filter.CorsFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

/**
* @Description: 统计报表接口启动类
* @Param: 
* @return: 
* @Author: 罗顺锋
* @Date: 2018/4/24
*/
//@ServletComponentScan
@SpringBootApplication

@EnableScheduling
@PropertySource(value = {"classpath:dubbo.properties","classpath:dasouche.properties","classpath:file.properties", "classpath:redis-config.properties","classpath:kafka.properties","classpath:oos.properties"})
@ImportResource("classpath:dubbo/dubbox-customer.xml")
public class MarketApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarketApiApplication.class, args);
	}

	/**
	 * 跨域过滤器
	 *
	 * @return
	 */
	@Bean
	public CorsFilter corsFilter() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowCredentials(true);
		// 设置你要允许的网站域名，如果全允许则设为 *
		config.addAllowedOrigin("*");
		// 如果要限制 HEADER 或 METHOD 请自行更改
		config.addAllowedHeader(CorsConfiguration.ALL);
		config.addAllowedMethod(CorsConfiguration.ALL);
		source.registerCorsConfiguration("/**", config);
		FilterRegistrationBean bean = new FilterRegistrationBean(new CorsFilter(source));
		// 重要
		bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return new CorsFilter(source);
	}

//	/**
//	 * 编码设置
//	 *
//	 * @return
//	 */
//	@Bean
//	public FilterRegistrationBean filterRegistrationBean() {
//		FilterRegistrationBean registrationBean = new FilterRegistrationBean();
//
//		CharacterEncodingFilter characterEncodingFilter = new CharacterEncodingFilter();
//
//		characterEncodingFilter.setEncoding("UTF-8");
//
//		registrationBean.setFilter(characterEncodingFilter);
//		return registrationBean;
//	}

//	@InitBinder
//	public void InitBinder(WebDataBinder dataBinder) {
//		dataBinder.registerCustomEditor(Date.class, new PropertyEditorSupport()
//		{
//			public void setAsText(String value) {
//				try {
//					setValue(new SimpleDateFormat("yyyy-MM-dd").parse(value));
//				} catch(ParseException e) { setValue(null);
//				}
//			}
//			public String getAsText() {
//				return new SimpleDateFormat("yyyy-MM-dd").format((Date) getValue());
//			}
//		});
//	}
}
