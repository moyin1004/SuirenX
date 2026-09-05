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

	db, err := database.Open(databasePath)
	if err != nil {
		log.Fatalf("open database: %v", err)
	}

	repo := repository.NewGormAssetRepository(db)
	assetService := service.NewAssetService(repo)
	if err := assetService.SeedExamples(); err != nil {
		log.Fatalf("seed examples: %v", err)
	}

	transport.NewServer(address, assetService).Run()
}

func envOrDefault(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}
