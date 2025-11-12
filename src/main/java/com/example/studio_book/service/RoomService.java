package com.example.studio_book.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.studio_book.entity.Room;
import com.example.studio_book.entity.User;
import com.example.studio_book.form.RoomEditForm;
import com.example.studio_book.form.RoomRegisterForm;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.repository.UserRepository;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository; // ← 追加

    public RoomService(RoomRepository roomRepository, UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository; // ← 追加
    }

    // すべてのスタジオをページングされた状態で取得する
    public Page<Room> findAllRooms(Pageable pageable) {
        return roomRepository.findAll(pageable);
    }  
    
    // 指定されたキーワードをスタジオ名に含むスタジオを、ページングされた状態で取得する
    public Page<Room> findRoomByNameLike(String keyword, Pageable pageable) {
        return roomRepository.findByNameLike("%" + keyword + "%", pageable);
    }    
    
    // 指定したidを持つスタジオを取得する
    public Optional<Room> findRoomById(Integer id) {
        return roomRepository.findById(id);
    }   
    
    
    // 指定されたキーワードを民宿名または住所に含む民宿を作成日時が新しい順に並べ替え、ページングされた状態で取得する
    public Page<Room> findRoomsByNameLikeOrAddressLikeOrderByCreatedAtDesc(String nameKeyword, String addressKeyword, Pageable pageable) {
        return roomRepository.findByNameLikeOrAddressLikeOrderByCreatedAtDesc("%" + nameKeyword + "%", "%" + addressKeyword + "%", pageable);
    }

    // 指定されたキーワードを民宿名または住所に含む民宿を宿泊料金が安い順に並べ替え、ページングされた状態で取得する
    public Page<Room> findRoomsByNameLikeOrAddressLikeOrderByPriceAsc(String nameKeyword, String addressKeyword, Pageable pageable) {
        return roomRepository.findByNameLikeOrAddressLikeOrderByPriceAsc("%" + nameKeyword + "%", "%" + addressKeyword + "%", pageable);
    }

    // 指定されたキーワードを住所に含む民宿を作成日時が新しい順に並べ替え、ページングされた状態で取得する
    public Page<Room> findRoomsByAddressLikeOrderByCreatedAtDesc(String area, Pageable pageable) {
        return roomRepository.findByAddressLikeOrderByCreatedAtDesc("%" + area + "%", pageable);
    }

    // 指定されたキーワードを住所に含む民宿を宿泊料金が安い順に並べ替え、ページングされた状態で取得する
    public Page<Room> findRoomsByAddressLikeOrderByPriceAsc(String area, Pageable pageable) {
        return roomRepository.findByAddressLikeOrderByPriceAsc("%" + area + "%", pageable);
    }

    // 指定された宿泊料金以下の民宿を作成日時が新しい順に並べ替え、ページングされた状態で取得する
    public Page<Room> findRoomsByPriceLessThanEqualOrderByCreatedAtDesc(Integer price, Pageable pageable) {
        return roomRepository.findByPriceLessThanEqualOrderByCreatedAtDesc(price, pageable);
    }

    // 指定された宿泊料金以下の民宿を宿泊料金が安い順に並べ替え、ページングされた状態で取得する
    public Page<Room> findRoomsByPriceLessThanEqualOrderByPriceAsc(Integer price, Pageable pageable) {
        return roomRepository.findByPriceLessThanEqualOrderByPriceAsc(price, pageable);
    }

    // すべての民宿を作成日時が新しい順に並べ替え、ページングされた状態で取得する
    public Page<Room> findAllRoomsByOrderByCreatedAtDesc(Pageable pageable) {
        return roomRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    // すべての民宿を宿泊料金が安い順に並べ替え、ページングされた状態で取得する
    public Page<Room> findAllRoomsByOrderByPriceAsc(Pageable pageable) {
        return roomRepository.findAllByOrderByPriceAsc(pageable);
    }    
    
    // 作成日時が新しい順に8件の民宿を取得する
    public List<Room> findTop8RoomsByOrderByCreatedAtDesc() {
        return roomRepository.findTop8ByOrderByCreatedAtDesc();
    }

    // 予約数が多い順に3件の民宿を取得する
    public List<Room> findTop3RoomsByOrderByReservationCountDesc() {
        return roomRepository.findAllByOrderByReservationCountDesc(PageRequest.of(0, 3));
    }  
    
    @Transactional
    public void createHouse(RoomRegisterForm roomRegisterForm) {
        Room room = new Room();
        MultipartFile imageFile = roomRegisterForm.getImageFile();

        if (!imageFile.isEmpty()) {
            String imageName = imageFile.getOriginalFilename();
            String hashedImageName = generateNewFileName(imageName);
            Path filePath = Paths.get("src/main/resources/static/storage/" + hashedImageName);
            copyImageFile(imageFile, filePath);
            room.setImageName(hashedImageName);
        }

        room.setName(roomRegisterForm.getName());
        room.setDescription(roomRegisterForm.getDescription());
        room.setPrice(roomRegisterForm.getPrice());
        room.setCapacity(roomRegisterForm.getCapacity());
        room.setPostalCode(roomRegisterForm.getPostalCode());
        room.setAddress(roomRegisterForm.getAddress());
        // Room が ManyToOne User の場合
        User host = userRepository.findById(roomRegisterForm.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("指定したホストが存在しません: " +roomRegisterForm.getUserId()));
        room.setUser(host);

        roomRepository.save(room);
    }
    
    @Transactional
    public void updateRoom(RoomEditForm roomEditForm, Room room) {
        MultipartFile imageFile = roomEditForm.getImageFile();

        if (!imageFile.isEmpty()) {
            String imageName = imageFile.getOriginalFilename();
            String hashedImageName = generateNewFileName(imageName);
            Path filePath = Paths.get("src/main/resources/static/storage/" + hashedImageName);
            copyImageFile(imageFile, filePath);
            room.setImageName(hashedImageName);
        }

        room.setName(roomEditForm.getName());
        room.setDescription(roomEditForm.getDescription());
        room.setPrice(roomEditForm.getPrice());
        room.setCapacity(roomEditForm.getCapacity());
        room.setPostalCode(roomEditForm.getPostalCode());
        room.setAddress(roomEditForm.getAddress());
        // Room が ManyToOne User の場合
        User host = userRepository.findById(roomEditForm.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("指定したホストが存在しません: " +roomEditForm.getUserId()));
        room.setUser(host);

        roomRepository.save(room);
    } 
    
    @Transactional
    public void deleteRoom(Room room) {
        roomRepository.delete(room);
    }    

    // UUIDを使って生成したファイル名を返す
    public String generateNewFileName(String fileName) {
        String[] fileNames = fileName.split("\\.");

        for (int i = 0; i < fileNames.length - 1; i++) {
            fileNames[i] = UUID.randomUUID().toString();
        }

        String hashedFileName = String.join(".", fileNames);

        return hashedFileName;
    }

    // 画像ファイルを指定したファイルにコピーする
    public void copyImageFile(MultipartFile imageFile, Path filePath) {
        try {
            Files.copy(imageFile.getInputStream(), filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    } 
    @Transactional(readOnly = true)
    public void assertOwnedBy(Integer roomId, Integer hostId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Room.user.id とログイン中ユーザIDを比較
        if (!room.getUser().getId().equals(hostId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
    @Transactional(readOnly = true)
    public Room findOwned(Integer roomId, Integer hostId) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // ✅ 所有者チェック
        //   Room に user を持っている前提：
        if (!room.getUser().getId().equals(hostId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return room;
    }
    
    public boolean existsByNameAndAddress(String name, String address) {
        return roomRepository.existsByNameAndAddress(name, address);
    }

    public boolean existsByNameAndAddressExcludingId(String name, String address, Integer excludeId) {
        return roomRepository.existsByNameAndAddressAndIdNot(name, address, excludeId);
    }
    
}
