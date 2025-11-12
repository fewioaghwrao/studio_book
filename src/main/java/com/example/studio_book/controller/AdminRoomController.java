package com.example.studio_book.controller;

import java.util.Optional;

import org.springframework.beans.propertyeditors.StringTrimmerEditor; // ★ 追加
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;                 // ★ 追加
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;      // ★ 追加
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.studio_book.entity.Room;
import com.example.studio_book.form.RoomEditForm;
import com.example.studio_book.form.RoomRegisterForm;
import com.example.studio_book.repository.UserRepository;
import com.example.studio_book.service.RoomService;

@Controller
@RequestMapping("/admin/rooms")
public class AdminRoomController {
    private final RoomService roomService;
    private final UserRepository userRepository; 

    public AdminRoomController(RoomService roomService, UserRepository userRepository) {
        this.roomService = roomService;
        this.userRepository = userRepository; 
    }
    
    // ★ 前後の空白をトリム（null にはしない）
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }

    @GetMapping
    public String index(@RequestParam(name = "keyword", required = false) String keyword,
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Direction.ASC) Pageable pageable,
            Model model)
{        
        Page<Room> roomPage;

        if (keyword != null && !keyword.isEmpty()) {
            roomPage = roomService.findRoomByNameLike(keyword, pageable);
        } else {
            roomPage = roomService.findAllRooms(pageable);
        }        

        model.addAttribute("roomPage", roomPage);
        model.addAttribute("keyword", keyword);

        return "admin/rooms/index";
    }
    @GetMapping("/{id}")
    public String show(@PathVariable(name = "id") Integer id, RedirectAttributes redirectAttributes, Model model) {
        Optional<Room> optionalRoom  = roomService.findRoomById(id);

        if (optionalRoom.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "スタジオが存在しません。");

            return "redirect:/admin/rooms";
        }

        Room room = optionalRoom.get();
        model.addAttribute("room", room);

        return "admin/rooms/show";
    }  
    
    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("roomRegisterForm", new RoomRegisterForm());
        model.addAttribute("hosts", userRepository.findAllHostsEnabled()); 

        return "admin/rooms/register";
    }  
    @PostMapping("/create")
    public String create(@ModelAttribute @Validated RoomRegisterForm roomRegisterForm,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model)
    {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roomRegisterForm", roomRegisterForm);
            model.addAttribute("hosts", userRepository.findAllHostsEnabled());

            return "admin/rooms/register";
        }
        // ★ 論理重複チェック（名称 × 住所）
        if (roomService.existsByNameAndAddress(roomRegisterForm.getName(), roomRegisterForm.getAddress())) {
            bindingResult.reject("duplicate.room", "同一の『スタジオ名 × 住所』のスタジオが既に存在します。");
            model.addAttribute("hosts", userRepository.findAllHostsEnabled());
            return "admin/rooms/register";
        }

        // 保存（★ DBユニーク違反の最終防衛線）
        try {
            roomService.createHouse(roomRegisterForm);
        } catch (DataIntegrityViolationException e) {
            bindingResult.reject("duplicate.room", "同一の『スタジオ名 × 住所』のスタジオが既に存在します。");
            model.addAttribute("hosts", userRepository.findAllHostsEnabled());
            return "admin/rooms/register";
        }

        roomService.createHouse(roomRegisterForm);
        redirectAttributes.addFlashAttribute("successMessage", "スタジオを登録しました。");

        return "redirect:/admin/rooms";
    }  
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable(name = "id") Integer id, RedirectAttributes redirectAttributes, Model model) {
        Optional<Room> optionalRoom = roomService.findRoomById(id);

        if (optionalRoom.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "スタジオが存在しません。");

            return "redirect:/admin/rooms";
        }

        Room room = optionalRoom.get();
        RoomEditForm roomEditForm = new RoomEditForm(room.getName(), null, room.getDescription(), room.getPrice(), 
        		room.getCapacity(), room.getPostalCode(), room.getAddress(), room.getUser() != null ? room.getUser().getId() : null // ← 修正版
        				);

        model.addAttribute("room", room);
        model.addAttribute("roomEditForm", roomEditForm);
        model.addAttribute("hosts", userRepository.findAllHostsEnabled()); // ★ hostsも追加

        return "admin/rooms/edit";
    } 
    
    @PostMapping("/{id}/update")
    public String update(@ModelAttribute @Validated RoomEditForm roomEditForm,
                         BindingResult bindingResult,
                         @PathVariable(name = "id") Integer id,
                         RedirectAttributes redirectAttributes,
                         Model model)
    {
        Optional<Room> optionalRoom = roomService.findRoomById(id);

        if (optionalRoom.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "スタジオが存在しません。");

            return "redirect:/admin/rooms";
        }

        Room room = optionalRoom.get();

        if (bindingResult.hasErrors()) {
            model.addAttribute("room", room);
            model.addAttribute("roomEditForm", roomEditForm);

            return "admin/rooms/edit";
        }
        
        // ★ 論理重複チェック（自分自身を除外）
        if (roomService.existsByNameAndAddressExcludingId(roomEditForm.getName(), roomEditForm.getAddress(), id)) {
            bindingResult.reject("duplicate.room", "同一の『スタジオ名 × 住所』のスタジオが既に存在します。");
            model.addAttribute("room", room);
            model.addAttribute("hosts", userRepository.findAllHostsEnabled()); // ★ 追加
            return "admin/rooms/edit";
        }

        try {
            roomService.updateRoom(roomEditForm, room);
        } catch (DataIntegrityViolationException e) {
            bindingResult.reject("duplicate.room", "同一の『スタジオ名 × 住所』のスタジオが既に存在します。");
            model.addAttribute("room", room);
            model.addAttribute("hosts", userRepository.findAllHostsEnabled()); // ★ 追加
            return "admin/rooms/edit";
        }

        roomService.updateRoom(roomEditForm, room);
        redirectAttributes.addFlashAttribute("successMessage", "スタジオ情報を編集しました。");

        return "redirect:/admin/rooms";
    }    
    
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable(name = "id") Integer id, RedirectAttributes redirectAttributes) {
        Optional<Room> optionalRoom = roomService.findRoomById(id);

        if (optionalRoom.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "スタジオが存在しません。");

            return "redirect:/admin/rooms";
        }

        Room room = optionalRoom.get();
        roomService.deleteRoom(room);
        redirectAttributes.addFlashAttribute("successMessage", "スタジオを削除しました。");

        return "redirect:/admin/rooms";
    }  
    
}
