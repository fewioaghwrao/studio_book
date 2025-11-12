package com.example.studio_book.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.studio_book.entity.AuditLog;
import com.example.studio_book.entity.Role;
import com.example.studio_book.entity.User;
import com.example.studio_book.entity.UserRole;
import com.example.studio_book.form.SignupForm;
import com.example.studio_book.form.UserEditForm;
import com.example.studio_book.repository.AuditLogRepository;
import com.example.studio_book.repository.RoleRepository;
import com.example.studio_book.repository.UserRepository;
import com.example.studio_book.repository.UserRoleRepository;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    
    // ★ 追加
    private final UserRoleRepository userRoleRepository;
    private final AuditLogRepository auditLogRepository;

    public UserService(UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            UserRoleRepository userRoleRepository,     // ★ 追加
            AuditLogRepository auditLogRepository) {   // ★ 追加
this.userRepository = userRepository;
this.roleRepository = roleRepository;
this.passwordEncoder = passwordEncoder;
this.userRoleRepository = userRoleRepository;
this.auditLogRepository = auditLogRepository;
}

    @Transactional
    public User createUser(SignupForm signupForm) {
        User user = new User();
        Role role = roleRepository.findById(signupForm.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("不正な利用区分が選択されました。"));

        user.setName(signupForm.getName());
        user.setFurigana(signupForm.getFurigana());
        user.setPostalCode(signupForm.getPostalCode());
        user.setAddress(signupForm.getAddress());
        user.setPhoneNumber(signupForm.getPhoneNumber());
        user.setEmail(signupForm.getEmail());
        user.setPassword(passwordEncoder.encode(signupForm.getPassword()));
        user.setRole(role);
        user.setEnabled(false);//メール認証無効にするときはtrue、有効にするときはfalse

        // 1) users に保存
        User saved = userRepository.save(user);


        // 2) user_roles に保存（重複回避）
        if (!userRoleRepository.existsByUserIdAndRoleId(saved.getId(), role.getId())) {
            userRoleRepository.save(new UserRole(saved, role));
        }


        // 3) audit_logs に保存
        AuditLog log = AuditLog.builder()
                .ts(LocalDateTime.now())
                .actorId(saved.getId())         // 実行者は自分
                .action("USER_SIGNUP")
                .entity("users")
                .entityId(saved.getId())
                .note("email=" + saved.getEmail() + ", role=" + role.getName())
                .build();

        auditLogRepository.save(log);

        return saved;
    }
    
    @Transactional
    public void updateUser(UserEditForm userEditForm, User user) {
        user.setName(userEditForm.getName());
        user.setFurigana(userEditForm.getFurigana());
        user.setPostalCode(userEditForm.getPostalCode());
        user.setAddress(userEditForm.getAddress());
        user.setPhoneNumber(userEditForm.getPhoneNumber());
        user.setEmail(userEditForm.getEmail());

        userRepository.save(user);
    }    
    
    @Transactional
    public void setEnabled(User user, boolean enabled, Integer actorId) {
        user.setEnabled(enabled);
        userRepository.save(user);

        if (actorId == null) {
            // セキュリティ上はここで例外にするのが安全
            throw new IllegalStateException("actorId is null while writing audit log");
            // もしくはシステムユーザーIDを使う運用なら、actorId = 1 等に置き換える
        }

        AuditLog log = AuditLog.builder()
                .ts(LocalDateTime.now())
                .actorId(actorId) // ★必須
                .action(enabled ? "USER_ENABLE" : "USER_DISABLE")
                .entity("users")
                .entityId(user.getId())
                .note("email=" + user.getEmail())
                .build();
        auditLogRepository.save(log);
    }
    
    // メールアドレスが登録済みかどうかをチェックする
    public boolean isEmailRegistered(String email) {
        User user = userRepository.findByEmail(email);
        return user != null;
    }    
    
    // パスワードとパスワード（確認用）の入力値が一致するかどうかをチェックする
    public boolean isSamePassword(String password, String passwordConfirmation) {
        return password.equals(passwordConfirmation);
    }
    
    // ユーザーを有効にする
    @Transactional
    public void enableUser(User user) {
        user.setEnabled(true);
        userRepository.save(user);
    }    
    
    // メールアドレスが変更されたかどうかをチェックする
    public boolean isEmailChanged(UserEditForm userEditForm, User user) {
        return !userEditForm.getEmail().equals(user.getEmail());
    }  
    
    // 指定したメールアドレスを持つユーザーを取得する
    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    // すべてのユーザーをページングされた状態で取得する
    public Page<User> findAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    // 指定されたキーワードを氏名またはフリガナに含むユーザーを、ページングされた状態で取得する
    public Page<User> findUsersByNameLikeOrFuriganaLike(String nameKeyword, String furiganaKeyword, Pageable pageable) {
        return userRepository.findByNameLikeOrFuriganaLike("%" + nameKeyword + "%", "%" + furiganaKeyword + "%", pageable);
    }    
    
    // 指定したidを持つユーザーを取得する
    public Optional<User> findUserById(Integer id) {
        return userRepository.findById(id);
    }   
    
}