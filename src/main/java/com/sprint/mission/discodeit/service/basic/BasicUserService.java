package com.sprint.mission.discodeit.service.basic;

import com.sprint.mission.discodeit.dto.data.UserDto;
import com.sprint.mission.discodeit.dto.request.BinaryContentCreateRequest;
import com.sprint.mission.discodeit.dto.request.UserCreateRequest;
import com.sprint.mission.discodeit.dto.request.UserUpdateRequest;
import com.sprint.mission.discodeit.entity.BinaryContent;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.entity.UserStatus;
import com.sprint.mission.discodeit.repository.BinaryContentRepository;
import com.sprint.mission.discodeit.repository.UserRepository;
import com.sprint.mission.discodeit.repository.UserStatusRepository;
import com.sprint.mission.discodeit.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class BasicUserService implements UserService {
    private final UserRepository userRepository;
    //
    private final BinaryContentRepository binaryContentRepository;
    private final UserStatusRepository userStatusRepository;

    @Override
    public UserDto create(UserCreateRequest userCreateRequest) {
        String username = userCreateRequest.username();
        String email = userCreateRequest.email();
        Optional<BinaryContentCreateRequest> optionalProfileCreateRequest = userCreateRequest.profileImageDto();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("User with email " + email + " already exists");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("User with username " + username + " already exists");
        }

        UUID nullableProfileId = optionalProfileCreateRequest
                .filter(dto -> dto.file() != null && !dto.file().isEmpty())
                .map(dto -> {
                    try {
                        byte[] bytes = dto.file().getBytes();
                        BinaryContent binaryContent = new BinaryContent(
                                dto.fileName(),
                                (long) bytes.length,
                                dto.contentType(),
                                bytes
                        );
                        return binaryContentRepository.save(binaryContent).getId();
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Failed to read file", e);
                    }
                }).orElse(null);

        String password = userCreateRequest.password();
        User user = new User(username, email, password, nullableProfileId);
        User createdUser = userRepository.save(user);

        Instant now = Instant.now();
        UserStatus userStatus = new UserStatus(createdUser.getId(), now);
        userStatusRepository.save(userStatus);

        return toDto(createdUser);
    }

    @Override
    public UserDto find(UUID userId) {
        return userRepository.findById(userId)
                .map(this::toDto)
                .orElseThrow(() -> new NoSuchElementException("User with id " + userId + " not found"));
    }

    @Override
    public List<UserDto> findAll() {
        return userRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public UserDto update(UUID userId, UserUpdateRequest userUpdateRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User with id " + userId + " not found"));

        String newUsername = userUpdateRequest.newUsername();
        String newEmail = userUpdateRequest.newEmail();
        Optional<BinaryContentCreateRequest> optionalNewProfileImage = userUpdateRequest.newProfileImageDto();

        if (userRepository.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("User with email " + newEmail + " already exists");
        }
        if (userRepository.existsByUsername(newUsername)) {
            throw new IllegalArgumentException("User with username " + newUsername + " already exists");
        }

        UUID nullableProfileId = optionalNewProfileImage
                .map(profileRequest -> {
                    try {
                        Optional.ofNullable(user.getProfileId())
                                .ifPresent(binaryContentRepository::deleteById);

                        String fileName = profileRequest.fileName();
                        String contentType = profileRequest.contentType();
                        byte[] bytes = profileRequest.file().getBytes();
                        BinaryContent binaryContent = new BinaryContent(fileName, (long) bytes.length, contentType, bytes);
                        return binaryContentRepository.save(binaryContent).getId();
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Failed to read profile img file", e);
                    }
                })
                .orElse(null);

        String newPassword = userUpdateRequest.newPassword();
        user.update(newUsername, newEmail, newPassword, nullableProfileId);

        return toDto(userRepository.save(user));
    }

    @Override
    public void delete(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User with id " + userId + " not found"));

        Optional.ofNullable(user.getProfileId())
                .ifPresent(binaryContentRepository::deleteById);
        userStatusRepository.deleteByUserId(userId);

        userRepository.deleteById(userId);
    }

    private UserDto toDto(User user) {
        Boolean online = userStatusRepository.findByUserId(user.getId())
                .map(UserStatus::isOnline)
                .orElse(null);

        return new UserDto(
                user.getId(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getUsername(),
                user.getEmail(),
                user.getProfileId(),
                online
        );
    }
}
