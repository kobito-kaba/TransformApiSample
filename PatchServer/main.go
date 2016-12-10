package main

import (
	"net/http"
	"os"
	"path/filepath"
	"io/ioutil"
	"strconv"
)

func handler(w http.ResponseWriter, r *http.Request) {
	// mainの置かれているディレクトリ
	// go run main.goだと、キャッシュ領域が返ってくるため、
	// うまく動かないので注意
	dir, _ := filepath.Abs(filepath.Dir(os.Args[0]))

	// パッチが置かれているはずのディレクトリパスを生成
	dirPath := filepath.Join(dir, "patch")

	// パッチファイルを探す
	patch := findPatchFile(dirPath)

	if patch != nil {
		// ファイルのフルパス
		patchPath := filepath.Join(dirPath, patch.Name())
		// ファイルを開く（エラーハンドリングは省略）
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
	}
}

func findPatchFile(dirPath string) os.FileInfo {
	// ディレクトリ中のファイル一覧
	files, _ := ioutil.ReadDir(dirPath)

	for _, f := range files {
		if filepath.Ext(f.Name()) == ".patch" {
			return f
		}
	}

	return nil
}

func main() {
	http.HandleFunc("/", handler) // ハンドラを登録してウェブページを表示させる
	http.ListenAndServe(":8080", nil)
}