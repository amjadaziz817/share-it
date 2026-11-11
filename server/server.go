// Package server shares a folder over WebDAV, so Finder (⌘K) can browse it and copy files in and out.
// The Android app calls it through gomobile.
package server

import (
	"context"
	"crypto/subtle"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"os"
	"path"
	"path/filepath"
	"strings"
	"time"

	"golang.org/x/net/webdav"
)

var srv *http.Server

// Scanner gets the full path of each file or folder that a client adds, changes or deletes.
// Android must scan these paths, or Gallery and the Files app do not show the change.
type Scanner interface {
	Scan(path string)
}

// Start serves root on port. Every request must send user and pass (HTTP basic auth).
func Start(root string, port int, user, pass string, scanner Scanner) error {
	Stop()
	ln, err := net.Listen("tcp", fmt.Sprintf(":%d", port))
	if err != nil {
		return err
	}
	// No WriteTimeout: large files can take minutes.
	srv = &http.Server{Handler: handler(root, user, pass, scanner), ReadHeaderTimeout: 10 * time.Second}
	go srv.Serve(ln)
	return nil
}

// Stop closes the server. It is safe to call when no server runs.
func Stop() {
	if srv != nil {
		srv.Close()
		srv = nil
	}
}

func handler(root, user, pass string, scanner Scanner) http.Handler {
	// Finder mounts a share read-only unless the server supports LOCK, so a lock system is necessary.
	dav := &webdav.Handler{FileSystem: noMacFiles{disk: webdav.Dir(root), mem: webdav.NewMemFS()}, LockSystem: webdav.NewMemLS()}
	// Clean against "/" first, so ".." cannot point outside root.
	full := func(p string) string { return filepath.Join(root, filepath.FromSlash(path.Clean("/"+p))) }
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		u, p, ok := r.BasicAuth()
		if !ok || subtle.ConstantTimeCompare([]byte(u), []byte(user)) != 1 || subtle.ConstantTimeCompare([]byte(p), []byte(pass)) != 1 {
			w.Header().Set("WWW-Authenticate", `Basic realm="share-it"`)
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		sw := &statusWriter{ResponseWriter: w, code: http.StatusOK}
		dav.ServeHTTP(sw, r)
		if scanner == nil || sw.code >= 300 {
			return
		}
		switch r.Method {
		case "PUT", "DELETE", "MKCOL":
			scanner.Scan(full(r.URL.Path))
		case "MOVE", "COPY":
			if d, err := url.Parse(r.Header.Get("Destination")); err == nil {
				scanner.Scan(full(d.Path))
			}
			if r.Method == "MOVE" {
				scanner.Scan(full(r.URL.Path))
			}
		}
	})
}

// noMacFiles keeps Finder's metadata files ("._name", ".DS_Store") in memory, not on the phone.
// Finder still reads back what it wrote, so copies do not fail, but phone apps never see these files.
type noMacFiles struct{ disk, mem webdav.FileSystem }

func isMacFile(name string) bool {
	b := path.Base(name)
	return strings.HasPrefix(b, "._") || b == ".DS_Store"
}

// pick keeps a Mac file on disk if it is already there (copied before this filter existed).
// Without this, a folder listing finds the file on disk but cannot open it in memory, and the whole listing fails.
func (fs noMacFiles) pick(ctx context.Context, name string) webdav.FileSystem {
	if !isMacFile(name) {
		return fs.disk
	}
	if _, err := fs.disk.Stat(ctx, name); err == nil {
		return fs.disk
	}
	return fs.mem
}

func (fs noMacFiles) Mkdir(ctx context.Context, name string, perm os.FileMode) error {
	return fs.pick(ctx, name).Mkdir(ctx, name, perm)
}

func (fs noMacFiles) OpenFile(ctx context.Context, name string, flag int, perm os.FileMode) (webdav.File, error) {
	target := fs.pick(ctx, name)
	if target == fs.mem && flag&os.O_CREATE != 0 {
		// The in-memory store needs the parent folders, so make them first.
		dir := ""
		for _, part := range strings.Split(strings.Trim(path.Dir(name), "/"), "/") {
			if part != "" {
				dir += "/" + part
				fs.mem.Mkdir(ctx, dir, 0o755) // an "already exists" error is expected
			}
		}
	}
	return target.OpenFile(ctx, name, flag, perm)
}

func (fs noMacFiles) RemoveAll(ctx context.Context, name string) error {
	return fs.pick(ctx, name).RemoveAll(ctx, name)
}

func (fs noMacFiles) Rename(ctx context.Context, oldName, newName string) error {
	// A file stays in the store where it is now. Only a Mac file in memory can keep a Mac file name.
	from := fs.pick(ctx, oldName)
	to := fs.disk
	if from == fs.mem && isMacFile(newName) {
		to = fs.mem
	}
	if from != to {
		return os.ErrPermission // a rename from memory to a normal file name is not supported
	}
	return from.Rename(ctx, oldName, newName)
}

func (fs noMacFiles) Stat(ctx context.Context, name string) (os.FileInfo, error) {
	return fs.pick(ctx, name).Stat(ctx, name)
}

type statusWriter struct {
	http.ResponseWriter
	code int
}

func (s *statusWriter) WriteHeader(code int) {
	s.code = code
	s.ResponseWriter.WriteHeader(code)
}
