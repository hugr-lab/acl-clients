// Connect to a duckdb-acl node over Arrow Flight SQL with ADBC, and read as a principal.
//
// Two ways to authenticate, the same two every example here shows:
//
//	ACL_TOKEN=<jwt>                    a token your IdP issued (sent as `authorization: Bearer`)
//	ACL_USER=<user> ACL_PASSWORD=<pw>  the node's password handshake: the node exchanges them at the IdP
//	                                   (the issuer's CLIENT ID, OAuth password grant) - TLS only
//
//	ACL_URI       grpc+tls://localhost:32800 by default (dev/node.sh)
//	ACL_INSECURE  1 to accept the dev node's self-signed certificate
//
//	ACL_TOKEN=$(../../dev/token.sh) ACL_INSECURE=1 go run .
package main

import (
	"context"
	"fmt"
	"log"
	"os"

	"github.com/apache/arrow-adbc/go/adbc"
	"github.com/apache/arrow-adbc/go/adbc/driver/flightsql"
	"github.com/apache/arrow-go/v18/arrow/memory"
)

func env(name, fallback string) string {
	if v := os.Getenv(name); v != "" {
		return v
	}
	return fallback
}

func main() {
	ctx := context.Background()
	opts := map[string]string{
		adbc.OptionKeyURI: env("ACL_URI", "grpc+tls://localhost:32800"),
		// the node's session is the client's connection, identified by a cookie the client must echo
		flightsql.OptionCookieMiddleware: adbc.OptionValueEnabled,
	}
	if os.Getenv("ACL_INSECURE") == "1" {
		opts[flightsql.OptionSSLSkipVerify] = adbc.OptionValueEnabled
	}
	switch {
	case os.Getenv("ACL_TOKEN") != "":
		opts[flightsql.OptionAuthorizationHeader] = "Bearer " + os.Getenv("ACL_TOKEN")
	case os.Getenv("ACL_USER") != "":
		// the driver runs the Flight BasicAuth handshake; the node answers with the IdP's bearer
		opts[adbc.OptionKeyUsername] = os.Getenv("ACL_USER")
		opts[adbc.OptionKeyPassword] = os.Getenv("ACL_PASSWORD")
	default:
		log.Fatal("set ACL_TOKEN, or ACL_USER and ACL_PASSWORD")
	}

	db, err := flightsql.NewDriver(memory.DefaultAllocator).NewDatabase(opts)
	if err != nil {
		log.Fatal(err)
	}
	defer db.Close()
	conn, err := db.Open(ctx)
	if err != nil {
		log.Fatal(err)
	}
	defer conn.Close()

	// row-level security: the token's tenant claim filters every read
	stmt, err := conn.NewStatement()
	if err != nil {
		log.Fatal(err)
	}
	defer stmt.Close()
	if err := stmt.SetSqlQuery("SELECT id, tenant, amount FROM orders ORDER BY id"); err != nil {
		log.Fatal(err)
	}
	reader, _, err := stmt.ExecuteQuery(ctx)
	if err != nil {
		log.Fatal(err)
	}
	defer reader.Release()
	for reader.Next() {
		rec := reader.Record()
		for row := 0; row < int(rec.NumRows()); row++ {
			fmt.Printf("%v\t%v\t%v\n", rec.Column(0).ValueStr(row), rec.Column(1).ValueStr(row), rec.Column(2).ValueStr(row))
		}
	}
	if err := reader.Err(); err != nil {
		log.Fatal(err)
	}
}
