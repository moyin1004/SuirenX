package main

import (
	"bufio"
	"errors"
	"flag"
	"fmt"
	"os"
	"strings"

	"github.com/moyin1004/suirenx/services/api/internal/auth"
	"github.com/moyin1004/suirenx/services/api/internal/database"
	"github.com/moyin1004/suirenx/services/api/internal/repository"
)

func main() {
	databasePath := flag.String("database", envOrDefault("SUIRENX_DATABASE_PATH", "data/suirenx.db"), "SQLite database file")
	username := flag.String("username", "", "configured superadmin username")
	flag.Parse()
	if strings.TrimSpace(*username) == "" {
		fail(errors.New("-username is required"))
	}
	stdinInfo, err := os.Stdin.Stat()
	if err != nil {
		fail(fmt.Errorf("inspect password input: %w", err))
	}
	if stdinInfo.Mode()&os.ModeCharDevice != 0 {
		fail(errors.New("pipe the new password from a secret manager; interactive terminal input is disabled"))
	}
	reader := bufio.NewScanner(os.Stdin)
	reader.Buffer(make([]byte, 128), 1024)
	if !reader.Scan() {
		fail(errors.New("read new password from stdin"))
	}
	password := reader.Text()
	if reader.Scan() {
		fail(errors.New("password input must contain exactly one line"))
	}
	if err := reader.Err(); err != nil {
		fail(fmt.Errorf("read new password: %w", err))
	}
	db, err := database.Open(*databasePath)
	if err != nil {
		fail(fmt.Errorf("open database: %w", err))
	}
	sqlDB, err := db.DB()
	if err != nil {
		fail(fmt.Errorf("get database handle: %w", err))
	}
	defer sqlDB.Close()
	admin, err := auth.NewService(repository.NewGormAuthRepository(db), []byte(os.Getenv("SUIRENX_JWT_SECRET")))
	if err != nil {
		fail(fmt.Errorf("load server auth configuration: %w", err))
	}
	if err := admin.RotateSuperadminPassword(*username, password); err != nil {
		fail(err)
	}
	password = ""
	fmt.Fprintln(os.Stdout, "superadmin password rotated")
}

func envOrDefault(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

func fail(err error) {
	fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}
