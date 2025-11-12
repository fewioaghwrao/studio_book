package com.example.studio_book.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests((requests) -> requests
                .requestMatchers("/error","/error/**","/css/**", "/images/**", "/js/**", "/storage/**", "/", 
                		"/signup/**", "/rooms", "/rooms/{id}", "/stripe/webhook","/terms", 
                		"/privacy", "/legal/**",
                		"/password/forgot",  
                		"/rooms/*/calendar/**",
                		 "/password/reset").permitAll()  // すべてのユーザーにアクセスを許可するURL
                .requestMatchers("/host/**").hasRole("HOST")// ホストにのみアクセスを許可するURL
                .requestMatchers("/admin/**").hasRole("ADMIN")  // 管理者にのみアクセスを許可するURL
                .anyRequest().authenticated()                   // 上記以外のURLはログインが必要（会員または管理者のどちらでもOK）
            )
            .formLogin((form) -> form
                .loginPage("/login")              // ログインページのURL
                .loginProcessingUrl("/perform_login")     // ログインフォームの送信先URL
                .defaultSuccessUrl("/?loggedIn")  // ログイン成功時のリダイレクト先URL
                .failureUrl("/login?error")       // ログイン失敗時のリダイレクト先URL
                .permitAll()
            )
            .logout((logout) -> logout
                .logoutSuccessUrl("/?loggedOut")  // ログアウト時のリダイレクト先URL
                .permitAll()
                    )
            .csrf(csrf -> csrf.ignoringRequestMatchers(
            	    new AntPathRequestMatcher("/stripe/webhook")
            	    // 必要に応じて new AntPathRequestMatcher("/stripe/webhook/**")
            	));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
