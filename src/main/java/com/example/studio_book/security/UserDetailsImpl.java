package com.example.studio_book.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.studio_book.entity.User;

public class UserDetailsImpl implements UserDetails, Serializable {
    private final User user;
    private final Collection<GrantedAuthority> authorities;

    /** 便利オーバーロード（権限なし） */
    public UserDetailsImpl(User user) {
        this(user, List.of());
    }
    
    public UserDetailsImpl(User user, Collection<GrantedAuthority> authorities) {
        this.user = user;
        //this.authorities = authorities;
        // nullを許さず、外部変更の影響も受けないようにコピー
        this.authorities = (authorities == null) ? List.of() : List.copyOf(authorities);
    }

    public User getUser() {
        return user;
    }

    // ハッシュ化済みのパスワードを返す
    @Override
    public String getPassword() {
        //return user.getPassword();
    	  return user != null ? user.getPassword() : null;
    	
    }

    // ログイン時に利用するユーザー名（メールアドレス）を返す
    @Override
    public String getUsername() {
       // return user.getEmail();
        if (user == null) return null;
        // email 未設定なら id を暫定の識別子として返す（テストでemailセット忘れでも安全）
        String email = user.getEmail();
        return (email != null && !email.isBlank())
                ? email
                : ("id:" + user.getId());
    }

    // ロールのコレクションを返す
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    // アカウントが期限切れでなければtrueを返す
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // ユーザーがロックされていなければtrueを返す
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    // ユーザーのパスワードが期限切れでなければtrueを返す
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // ユーザーが有効であればtrueを返す
    @Override
    public boolean isEnabled() {
        //return user.getEnabled();
        // enabled が null の場合でも安全に false 扱い
        return user != null && Boolean.TRUE.equals(user.getEnabled());
    }
}
