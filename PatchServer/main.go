package main

import (
	"net/http"
	"os"
	"path/filepath"
	"io/ioutil"
	"strconv"
	"github.com/gorilla/mux"
	"encoding/json"
)

func main() {
	r := mux.NewRouter()

	r.HandleFunc("/patches/", FileListHandler).Methods("GET")
	r.HandleFunc("/patches/{fileName:[A-Za-z0-9_]+.patch}", FileDownloadHandler).Methods("GET")

	http.ListenAndServe(":8080", r)
}

func FileListHandler(w http.ResponseWriter, r *http.Request) {
	// パッチファイルの置かれるディレクトリ
	dirPath, _ := getPatchDir()

	files := []string{}

	findPatchFiles(dirPath, func(patch os.FileInfo) bool {
		files = append(files, patch.Name())
		return true
	})

	response, _ := json.Marshal(files)

	w.Write(response)
}

func FileDownloadHandler(w http.ResponseWriter, r *http.Request) {
	parameters := mux.Vars(r)
	fileName := parameters["fileName"]

	// パッチファイルの置かれるディレクトリ
	dirPath, _ := getPatchDir()

	// パッチファイルを探す
	findPatchFile(dirPath, fileName, func(patch os.FileInfo) {
		// ファイルのフルパス
		patchPath := filepath.Join(dirPath, patch.Name())

		file, _ := os.Open(patchPath)
		defer file.Close()

		// ファイルの最終更新時間
		modified := patch.ModTime().Format(http.TimeFormat)
		// ファイルのサイズ
		length := strconv.FormatInt(patch.Size(), 10)

		// ヘッダーを出力
		w.Header().Set("Content-Type", "application/octet-stream")
		w.Header().Set("Content-Length", length)
		w.Header().Set("Last-Modified", modified)
		w.Header().Set("Content-Disposition", "filename=" + patch.Name())
		w.WriteHeader(http.StatusOK)

		// ファイルを読み込んで、出力する
		buf := make([]byte, 1024)
		for {
			n, err := file.Read(buf)
			if n == 0 || err != nil {
				break
			}
			w.Write(buf[:n])
		}
	})
}

func getPatchDir() (string, error) {
	// mainの置かれているディレクトリ
	// go run main.goだと、キャッシュ領域が返ってくるため、
	// うまく動かないので注意
	dir, err := filepath.Abs(filepath.Dir(os.Args[0]))

	if err != nil {
		return "", err
	}

	// パッチが置かれているはずのディレクトリパスを生成
	dirPath := filepath.Join(dir, "patches")

	return dirPath, nil
}

func findPatchFile(dirPath string, fileName string, handler func(os.FileInfo)) {
	findPatchFiles(dirPath, func(file os.FileInfo) bool {
		if (file.Name() == fileName) {
			handler(file)
			return false
		}
		return true
	})
}

func findPatchFiles(dirPath string, handler func(os.FileInfo) bool) {
	// ディレクトリ中のファイル一覧
	files, _ := ioutil.ReadDir(dirPath)

	for _, f := range files {
		if filepath.Ext(f.Name()) == ".patch" && !handler(f) {
			return
		}
	}
}