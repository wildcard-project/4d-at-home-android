# 同梱動画とタイムライン

効果のタイムラインは、**同梱している動画ファイルそのもの**を解析して作っています。
別の切り出し・別のエンコードの動画を置くと、効果が全体に一律でずれます（2026-10-18 に実機で約 1 秒ずれた原因）。
動画を差し替えたら、タイムラインも作り直してください（生成元: wildcard-project/4DatHOME の `analysis-v4`）。

## Git に入っている動画

| ファイル | 長さ | 出典 |
|---|---|---|
| `app/src/main/assets/videos/sintel_trailer.mp4` | 52.2 秒 | Sintel 予告編 © Blender Foundation, CC-BY 3.0 (durian.blender.org) |
| `app/src/main/assets/videos/bbb_trailer.mp4` | 33.0 秒 | Big Buck Bunny 予告編 © Blender Foundation, CC-BY 3.0 (peach.blender.org)。元の .mov を再エンコードせず mp4 に詰め替え |
| `app/src/main/assets/videos/fourdx_promo.mp4` | 31.4 秒 | 4DX@HOME プロモーション（jphacks/kz_2504 同梱のもの） |

## Git に入れていない動画（市販作品）

Fast X 予告編は権利物なので、公開リポジトリには入れません。チーム内限定の Google ドライブから取得して置いてください。

- 置き場所: Google ドライブ「4DX@HOME 試験動画 (権利物・チーム内限定)」
  https://drive.google.com/drive/folders/12Y0UUzoV6GmhX6i7K9HmqHBBDKMgUomu
- ファイル: `wild_speed_fire_boost.mp4`（151.558 秒、103,478,635 バイト）
- 置く先: `app/src/main/assets/videos/wild_speed_fire_boost.mp4`（`.gitignore` 済み）
- 照合: 次の値と一致することを確認してください。一致しない場合はタイムラインとずれます。

```bash
shasum app/src/main/assets/videos/wild_speed_fire_boost.mp4
# b7934d7261b938b053668d963bbd2f7f9f1f6830
```

## Fast X のタイムライン（聴き比べ用に 3 枚のカード）

同じ動画に 3 種類のタイムラインを割り当て、ホーム画面に並べています。アプリを入れ直さずに比べられます。

| カード | タイムライン |
|---|---|
| ワイルド・スピード／ファイヤーブースト（手動） | `timelines/wild_speed_fire_boost.json`（従来の手動版） |
| 〃（v4 演出多め） | `timelines/wild_speed_fire_boost.rich.json` |
| 〃（v4 快適） | `timelines/wild_speed_fire_boost.comfort.json` |

他の 3 本は `timelines/<id>.json` が v4 演出多め、`<id>.rich.json` / `<id>.comfort.json` がモード別です。
