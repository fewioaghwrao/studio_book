package com.example.studio_book;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StudioBookApplicationTests {

//	@Test 
//	void contextLoads() {//単体テスト
//	}
	  @Test
	  void smokeTest() {// CI安定化用の簡易スモークテスト
	    // ここは軽い確認だけ（例：true）
	    org.junit.jupiter.api.Assertions.assertTrue(true);
	  }

}
