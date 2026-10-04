package service

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"
	"unicode"
	"unicode/utf8"

	"github.com/moyin1004/suirenx/services/api/internal/domain"
	"github.com/moyin1004/suirenx/services/api/internal/repository"
)

var (
	ErrConfigRateLimited = errors.New("configuration token rate limit exceeded")
	ErrInvalidConfigName = errors.New("invalid configuration display name")
)

type IssuedApiToken struct {
	Metadata domain.ApiToken
	Secret   string
}

type ConfigService struct {
	repository repository.ConfigRepository
	now        func() time.Time
	mu         sync.Mutex
	reads      map[string][]time.Time
	readChecks uint64
}

func NewConfigService(repository repository.ConfigRepository) *ConfigService {
	return &ConfigService{repository: repository, now: time.Now, reads: make(map[string][]time.Time)}
}

func (s *ConfigService) MaxConfigBytes() (int64, error) {
	limit, err := s.repository.MaxConfigBytes()
	if err != nil {
		return 0, fmt.Errorf("read configuration size limit: %w", err)
	}
	return limit, nil
}

func (s *ConfigService) SetMaxConfigBytes(limit int64, actorID string) error {
	if limit < 1 || actorID == "" {
		return repository.ErrInvalidConfigLimit
	}
	if err := s.repository.SetMaxConfigBytes(limit, actorID, s.now().UTC()); err != nil {
		return fmt.Errorf("set configuration size limit: %w", err)
	}
	return nil
}

func (s *ConfigService) AccountRegistrationEnabled() (bool, error) {
	enabled, err := s.repository.AccountRegistrationEnabled()
	if err != nil {
		return false, fmt.Errorf("read account registration setting: %w", err)
	}
	return enabled, nil
}

func (s *ConfigService) SetAccountRegistrationEnabled(enabled bool, actorID string) error {
	if actorID == "" {
		return errors.New("superadmin actor is required")
	}
	if err := s.repository.SetAccountRegistrationEnabled(enabled, actorID, s.now().UTC()); err != nil {
		return fmt.Errorf("set account registration setting: %w", err)
	}
	return nil
}

func (s *ConfigService) ListConfigs() ([]domain.ConfigFile, error) {
	configs, err := s.repository.ListConfigs()
	if err != nil {
		return nil, fmt.Errorf("list configuration files: %w", err)
	}
	if configs == nil {
		configs = []domain.ConfigFile{}
	}
	return configs, nil
}

func (s *ConfigService) GetConfig(key string) (*domain.ConfigFile, error) {
	config, err := s.repository.GetConfig(strings.TrimSpace(key))
	if err != nil {
		return nil, fmt.Errorf("get configuration file: %w", err)
	}
	return config, nil
}

func (s *ConfigService) CreateConfig(name, content, actorID string) (*domain.ConfigFile, error) {
	name, err := normalizeConfigName(name)
	if err != nil || actorID == "" || !utf8.ValidString(content) {
		return nil, repository.ErrInvalidConfig
	}
	if err := s.checkContentSize(content); err != nil {
		return nil, err
	}
	now := s.now().UTC()
	key, err := randomID("cfg_", 16)
	if err != nil {
		return nil, fmt.Errorf("generate configuration key: %w", err)
	}
	config := &domain.ConfigFile{Key: key, DisplayName: name, Content: content, CreatedAt: now, UpdatedAt: now}
	if err := s.repository.CreateConfig(config, actorID); err != nil {
		return nil, fmt.Errorf("create configuration file: %w", err)
	}
	return config, nil
}

func (s *ConfigService) UpdateConfig(key, name, content, actorID string) (*domain.ConfigFile, error) {
	key = strings.TrimSpace(key)
	name, err := normalizeConfigName(name)
	if err != nil || key == "" || actorID == "" || !utf8.ValidString(content) {
		return nil, repository.ErrInvalidConfig
	}
	if err := s.checkContentSize(content); err != nil {
		return nil, err
	}
	config, err := s.repository.UpdateConfig(key, name, content, actorID, s.now().UTC())
	if err != nil {
		return nil, fmt.Errorf("update configuration file: %w", err)
	}
	return config, nil
}

func (s *ConfigService) DeleteConfig(key, actorID string) error {
	if strings.TrimSpace(key) == "" || actorID == "" {
		return repository.ErrInvalidConfig
	}
	if err := s.repository.DeleteConfig(strings.TrimSpace(key), actorID, s.now().UTC()); err != nil {
		return fmt.Errorf("delete configuration file: %w", err)
	}
	return nil
}

func (s *ConfigService) ListApiTokens() ([]domain.ApiToken, error) {
	tokens, err := s.repository.ListApiTokens()
	if err != nil {
		return nil, fmt.Errorf("list API tokens: %w", err)
	}
	if tokens == nil {
		tokens = []domain.ApiToken{}
	}
	return tokens, nil
}

func (s *ConfigService) CreateApiToken(label, mode string, configKeys []string, expiresAt, actorID string) (*IssuedApiToken, error) {
	label = strings.TrimSpace(label)
	mode = strings.ToUpper(strings.TrimSpace(mode))
	if label == "" || len([]rune(label)) > 100 || actorID == "" || (mode != "BEARER" && mode != "QUERY") || len(configKeys) == 0 {
		return nil, repository.ErrInvalidApiToken
	}
	seen := make(map[string]struct{}, len(configKeys))
	keys := make([]string, 0, len(configKeys))
	for _, key := range configKeys {
		key = strings.TrimSpace(key)
		if key == "" {
			return nil, repository.ErrInvalidApiToken
		}
		if _, exists := seen[key]; !exists {
			seen[key] = struct{}{}
			keys = append(keys, key)
		}
	}
	var expiry *time.Time
	if strings.TrimSpace(expiresAt) != "" {
		parsed, err := time.Parse(time.RFC3339, expiresAt)
		if err != nil || !parsed.After(s.now()) {
			return nil, repository.ErrInvalidApiToken
		}
		parsed = parsed.UTC()
		expiry = &parsed
	}
	secret, err := randomID("sx_", 32)
	if err != nil {
		return nil, fmt.Errorf("generate API token: %w", err)
	}
	tokenID, err := randomID("tok_", 16)
	if err != nil {
		return nil, fmt.Errorf("generate API token id: %w", err)
	}
	hash := sha256.Sum256([]byte(secret))
	now := s.now().UTC()
	token := &domain.ApiToken{ID: tokenID, Label: label, TransportMode: mode, ConfigKeys: keys, CreatedAt: now, ExpiresAt: expiry}
	if err := s.repository.CreateApiToken(token, hex.EncodeToString(hash[:]), actorID); err != nil {
		return nil, fmt.Errorf("create API token: %w", err)
	}
	return &IssuedApiToken{Metadata: *token, Secret: secret}, nil
}

func (s *ConfigService) RevokeApiToken(id, actorID string) error {
	if strings.TrimSpace(id) == "" || actorID == "" {
		return repository.ErrInvalidApiToken
	}
	if err := s.repository.RevokeApiToken(strings.TrimSpace(id), actorID, s.now().UTC()); err != nil {
		return fmt.Errorf("revoke API token: %w", err)
	}
	return nil
}

func (s *ConfigService) UpdateApiToken(id, label string, configKeys []string, actorID string) error {
	id = strings.TrimSpace(id)
	label = strings.TrimSpace(label)
	if id == "" || actorID == "" || label == "" || len([]rune(label)) > 100 || len(configKeys) == 0 {
		return repository.ErrInvalidApiToken
	}
	seen := make(map[string]struct{}, len(configKeys))
	keys := make([]string, 0, len(configKeys))
	for _, key := range configKeys {
		key = strings.TrimSpace(key)
		if key == "" {
			return repository.ErrInvalidApiToken
		}
		if _, exists := seen[key]; exists {
			continue
		}
		seen[key] = struct{}{}
		keys = append(keys, key)
	}
	if err := s.repository.UpdateApiToken(id, label, keys, actorID, s.now().UTC()); err != nil {
		return fmt.Errorf("update API token: %w", err)
	}
	return nil
}

func (s *ConfigService) ReadContent(key, bearerToken, queryToken string) (string, error) {
	if key == "" || (bearerToken == "" && queryToken == "") || (bearerToken != "" && queryToken != "") {
		return "", repository.ErrApiTokenInvalid
	}
	secret, mode := bearerToken, "BEARER"
	if queryToken != "" {
		secret, mode = queryToken, "QUERY"
	}
	hash := sha256.Sum256([]byte(secret))
	digest := hex.EncodeToString(hash[:])
	content, err := s.repository.ReadConfigContent(key, digest, mode, s.now().UTC())
	if err != nil {
		return "", fmt.Errorf("read configuration content: %w", err)
	}
	if !s.allowRead(digest) {
		return "", ErrConfigRateLimited
	}
	return content, nil
}

func (s *ConfigService) checkContentSize(content string) error {
	limit, err := s.repository.MaxConfigBytes()
	if err != nil {
		return fmt.Errorf("read configuration size limit: %w", err)
	}
	if int64(len([]byte(content))) > limit {
		return repository.ErrConfigTooLarge
	}
	return nil
}

func (s *ConfigService) allowRead(tokenHash string) bool {
	now := s.now()
	cutoff := now.Add(-time.Minute)
	s.mu.Lock()
	defer s.mu.Unlock()
	active := s.reads[tokenHash][:0]
	for _, attempt := range s.reads[tokenHash] {
		if attempt.After(cutoff) {
			active = append(active, attempt)
		}
	}
	if len(active) >= 60 {
		s.reads[tokenHash] = active
		return false
	}
	s.reads[tokenHash] = append(active, now)
	s.readChecks++
	if s.readChecks%64 == 0 {
		for digest, attempts := range s.reads {
			if len(attempts) == 0 || !attempts[len(attempts)-1].After(cutoff) {
				delete(s.reads, digest)
			}
		}
	}
	return true
}

func normalizeConfigName(name string) (string, error) {
	name = strings.TrimSpace(name)
	if name == "" || len([]rune(name)) > 100 || !utf8.ValidString(name) || strings.ContainsAny(name, "\r\n") {
		return "", ErrInvalidConfigName
	}
	for _, char := range name {
		if unicode.IsControl(char) {
			return "", ErrInvalidConfigName
		}
	}
	return name, nil
}

func randomID(prefix string, size int) (string, error) {
	raw := make([]byte, size)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	return prefix + hex.EncodeToString(raw), nil
}
