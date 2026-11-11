package server

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path"
	"path/filepath"
	"strings"
	"testing"
)

func do(t *testing.T, h http.Handler, method, path, body string, auth bool) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	if auth {
		req.SetBasicAuth("phone", "secret")
	}
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	return rec
}

type scans []string

func (s *scans) Scan(p string) { *s = append(*s, p) }

func TestHandler(t *testing.T) {
	root := t.TempDir()
	var scanned scans
	h := handler(root, "phone", "secret", &scanned)

	if rec := do(t, h, "PROPFIND", "/", "", false); rec.Code != http.StatusUnauthorized {
		t.Fatalf("no auth: got %d, want 401", rec.Code)
	}
	if rec := do(t, h, "PUT", "/a.txt", "hello", true); rec.Code != http.StatusCreated {
		t.Fatalf("PUT: got %d", rec.Code)
	}
	if b, _ := os.ReadFile(filepath.Join(root, "a.txt")); string(b) != "hello" {
		t.Fatalf("file on disk: got %q", b)
	}
	if rec := do(t, h, "GET", "/a.txt", "", true); rec.Body.String() != "hello" {
		t.Fatalf("GET: got %d %q", rec.Code, rec.Body.String())
	}
	if rec := do(t, h, "PROPFIND", "/", "", true); rec.Code != http.StatusMultiStatus || !strings.Contains(rec.Body.String(), "a.txt") {
		t.Fatalf("PROPFIND: got %d", rec.Code)
	}
	if rec := do(t, h, "OPTIONS", "/", "", true); !strings.Contains(rec.Header().Get("DAV"), "2") {
		t.Fatalf("Finder needs DAV class 2 (LOCK) for write access, got %q", rec.Header().Get("DAV"))
	}

	req := httptest.NewRequest("MOVE", "/a.txt", nil)
	req.SetBasicAuth("phone", "secret")
	req.Header.Set("Destination", "http://example.com/b%20c.txt")
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusCreated {
		t.Fatalf("MOVE: got %d", rec.Code)
	}
	do(t, h, "PUT", "/../x.txt", "no", false) // fails auth, so no scan
	want := []string{filepath.Join(root, "a.txt"), filepath.Join(root, "b c.txt"), filepath.Join(root, "a.txt")}
	if strings.Join(scanned, "|") != strings.Join(want, "|") {
		t.Fatalf("scanned %q, want %q", scanned, want)
	}
	if got := filepath.Join(root, filepath.FromSlash(path.Clean("/"+"/../../etc"))); !strings.HasPrefix(got, root) {
		t.Fatalf("path escapes root: %s", got)
	}
}

func TestMacFilesStayOffDisk(t *testing.T) {
	root := t.TempDir()
	h := handler(root, "phone", "secret", nil)
	if rec := do(t, h, "MKCOL", "/show", "", true); rec.Code != http.StatusCreated {
		t.Fatalf("MKCOL: got %d", rec.Code)
	}
	for _, p := range []string{"/show/EP01.mp4", "/show/._EP01.mp4", "/show/.DS_Store"} {
		if rec := do(t, h, "PUT", p, "data", true); rec.Code != http.StatusCreated {
			t.Fatalf("PUT %s: got %d", p, rec.Code)
		}
	}
	// Finder reads its metadata back, so it must still work over WebDAV.
	if rec := do(t, h, "GET", "/show/._EP01.mp4", "", true); rec.Body.String() != "data" {
		t.Fatalf("GET ._ file: got %d %q", rec.Code, rec.Body.String())
	}
	entries, _ := os.ReadDir(filepath.Join(root, "show"))
	if len(entries) != 1 || entries[0].Name() != "EP01.mp4" {
		t.Fatalf("on disk: got %v, want only EP01.mp4", entries)
	}
}

// Regression: "._" files copied before the filter existed are on disk. The folder listing must still work,
// and Finder must be able to delete them.
func TestOldMacFilesOnDisk(t *testing.T) {
	root := t.TempDir()
	os.Mkdir(filepath.Join(root, "Movies"), 0o755)
	for _, n := range []string{"EP01.mp4", "._EP01.mp4"} {
		os.WriteFile(filepath.Join(root, "Movies", n), []byte("x"), 0o644)
	}
	h := handler(root, "phone", "secret", nil)

	req := httptest.NewRequest("PROPFIND", "/Movies/", nil)
	req.SetBasicAuth("phone", "secret")
	req.Header.Set("Depth", "1")
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusMultiStatus || !strings.Contains(rec.Body.String(), "EP01.mp4") {
		t.Fatalf("PROPFIND: got %d %s", rec.Code, rec.Body.String())
	}
	if rec := do(t, h, "DELETE", "/Movies/._EP01.mp4", "", true); rec.Code != http.StatusNoContent {
		t.Fatalf("DELETE old ._ file: got %d", rec.Code)
	}
	if _, err := os.Stat(filepath.Join(root, "Movies", "._EP01.mp4")); !os.IsNotExist(err) {
		t.Fatalf("old ._ file still on disk: %v", err)
	}
}
