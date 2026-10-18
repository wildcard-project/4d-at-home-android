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

Fast X と Michael の予告編は権利物なので、公開リポジトリには入れません。チーム内限定の Google ドライブから取得して置いてください。

- 置き場所: Google ドライブ「4DX@HOME 試験動画 (権利物・チーム内限定)」
  https://drive.google.com/drive/folders/12Y0UUzoV6GmhX6i7K9HmqHBBDKMgUomu
- 置く先はどちらも `app/src/main/assets/videos/`（`.gitignore` 済み）
- 照合: 次の値と一致することを確認してください。一致しない場合はタイムラインとずれます。

| ファイル | 長さ | バイト数 | sha1 |
|---|---|---|---|
| `wild_speed_fire_boost.mp4`（Fast X 予告） | 151.558 秒 | 103,478,635 | `b7934d7261b938b053668d963bbd2f7f9f1f6830` |
| `michael_trailer.mp4`（映画『Michael／マイケル』US版第1弾予告、キノフィルムズ） | 73.886 秒 | 17,174,872 | `ea7f13d13f053de424ea1fba8d113e6757f66ba8` |

```bash
shasum app/src/main/assets/videos/*.mp4
```

動画が無いカードは再生できないだけで、アプリの他の機能には影響しません。

## Fast X のタイムライン（聴き比べ用に 2 枚のカード）

同じ動画に 2 種類のタイムラインを割り当て、ホーム画面に並べています。アプリを入れ直さずに比べられます。

| カード | タイムライン |
|---|---|
| ワイルド・スピード／ファイヤーブースト（手動） | `timelines/wild_speed_fire_boost.json`（従来の手動版） |
| 〃（自動解析） | `timelines/wild_speed_fire_boost.auto.json`（v4.3） |

Michael と他の 3 本の `timelines/<id>.json` は自動解析 (v4.3) です。
