/* rolesテーブル */
INSERT IGNORE INTO roles (id, name) VALUES (1, 'ROLE_GENERAL');
INSERT IGNORE INTO roles (id, name) VALUES (2, 'ROLE_HOST');
INSERT IGNORE INTO roles (id, name) VALUES (3, 'ROLE_ADMIN');

/* usersテーブル */
INSERT IGNORE INTO users (id, name, furigana, postal_code, address, phone_number, email, password, role_id, enabled) VALUES (1, 'サンプル 太郎', 'サンプル タロウ', '101-0022', '東京都〇〇区', '090-1234-5678', 'user1@example.com', '$2a$10$2JNjTwZBwo7fprL2X4sv.OEKqxnVtsVQvuXDkI8xVGix.U3W5B7CO', 1, true);
INSERT IGNORE INTO users (id, name, furigana, postal_code, address, phone_number, email, password, role_id, enabled) VALUES (2, 'ホスト 次郎', 'ホスト ジロウ', '101-0022', '東京都〇〇区', '090-1234-5678', 'host1@example.com', '$2a$10$2JNjTwZBwo7fprL2X4sv.OEKqxnVtsVQvuXDkI8xVGix.U3W5B7CO', 2, true);
INSERT IGNORE INTO users (id, name, furigana, postal_code, address, phone_number, email, password, role_id, enabled) VALUES (3, 'アドミン 花子', 'アドミン ハナコ', '101-0022', '東京都〇〇区', '090-1234-5678', 'admin1@example.com', '$2a$10$2JNjTwZBwo7fprL2X4sv.OEKqxnVtsVQvuXDkI8xVGix.U3W5B7CO', 3, true);

/* homesテーブル */
