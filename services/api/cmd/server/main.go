package main

import (
	"log"
	"os"

	"github.com/moyin1004/suirenx/services/api/internal/database"
	"github.com/moyin1004/suirenx/services/api/internal/repository"
	"github.com/moyin1004/suirenx/services/api/internal/service"
	transport "github.com/moyin1004/suirenx/services/api/internal/transport/http"
)

func main() {
	databasePath := envOrDefault("SUIRENX_DATABASE_PATH", "data/suirenx.db")
	address := envOrDefault("SUIRENX_HTTP_ADDRESS", ":8888")
	jwtSecret := os.Getenv("SUIRENX_JWT_SECRET")
	if len([]byte(jwtSecret)) < 32 {
		log.Fatalf("SUIRENX_JWT_SECRET must be at least 32 bytes")
	}

	db, err := database.Open(databasePath)
	if err != nil {
		log.Fatalf("open database: %v", err)
	}

	repo := repository.NewGormAssetRepository(db)
	assetService := service.NewAssetService(repo)

	transport.NewServer(address, assetService, transport.WithM5(db, jwtSecret)).Run()
}

func envOrDefault(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}
