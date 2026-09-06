

# 自定义源、导入源



## 自定义源

目前仅支持yaml格式的源，以下是示例，以及支持设置的所有参数，参数含义基本与字段名意思差不多

`js_ttl` : 多久刷新一次 js文件

`url` : 访问的 网站链接


```
TV-GROUP:
  - 央视频(手机):
      - CCTV-4k 超清:
          url: https://www.yangshipin.cn/tv/home?pid=600002264
          js: https://xxxx/default_player.js
          js_ttl: 10
          doc_start_js: https://xxxx/default_player.js
          user_agent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"
          app_bridge: false
          timeout: 0
          allow_focus: false
          arg: cfg/default.json

      - 四川新闻综合:
          url: https://www.cditv.cn/show/4845-563.html
          js: https://xxxx/default_player.js
          js_ttl: 1
          doc_start_js: https://xxxx/default_player.js
          user_agent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"
          referer: "https://www.cditv.cn/list/4845/1.html"
          app_bridge: false
          timeout: 0
          allow_focus: false
          arg: cfg/default.json

```

## 自定义点击方式

打开链接后，支持配置默认点击或者某些动作，以下是使用介绍：

以下参数支持配置在 url 链接中，用 urlencode(base64(xxxxxxxxx)) 进行编码

例如(伪代码)：https://www.cditv.cn/show/4845-563.html?watch_error_text=urlencode(base64(xxxxxxxxx))

也可以配置在json中，用 `arg: cfg/default.json` 加载，两者均可，互相补充

```
{
    # 当检测到某个页面元素后，将这个页面元素的内容全屏展示，无法播放、错误等内容，自动从元素提取并显示
    "watch_error_text": [
        { "selector": "div.loading1-main" },
        { "selector": "#error_msg_player" },
        { "selector": "div.reason-tnUaKc" },
        { "selector": "#TipsLayout", "first_line": true, "skip_text": ["更多直播"] }
    ],


    # 检测到某个元素后，自动点击，支持 native_click 参数，调用网页 元素.click() 还是 webview.dispatchEvent()
    "watch_click": [
        { "selector": "#poster_yd_video_pay", "delay": 300 }
    ],


    # 当加载某个资源的时候，显示自定义全屏错误，一般用于 watch_error_text 无法通过元素匹配
    "watch_error_source": [
        { "source": "https://p.statickksmg.com/cont/2023/10/08/image_1696731269_qOxBpp34.jpg", "show_error": "本时段暂不提供网络直播服务" }
    ],


    # 当检测到某个元素后，显示自定义错误提示
    "watch_error_selector": [
        { "selector": "div.image-mask[style=\"\"]", "show_error": "本时段暂不提供网络直播服务" }
    ],


    # 某些节目在网页的一级菜单下，必须点击某个元素才可以播放的，用 watch_video_click
    # 注意：没有在一级菜单的，务必删除这个字段，否则卡住
    "watch_video_click": [
        { "selector": "#poster_yd_video_pay_1", "delay": 300, "native_click": false, "delay_call": 300}
    ],


    # 某些节目在网页的  二级菜单下，必须先点击 一级菜单，再点击 二级菜单的
    # 注意：没有在二级菜单的，务必删除这个字段，否则卡住
    "watch_video_click2": [
        { "selector": "#poster_yd_video_pay_1", "delay": 300, "native_click": false, "delay_call": 300}
    ]
}
```


## 导入源

目前没有设置页面、没有可供显示的其他页面，都被隐藏了，如果要手动导入源，需要自己找到 设备的 ip地址。默认使用 `8081` 端口

例如：  `http://192.168.1.185:8081`

支持两种导入方式，一种 url 链接自动导入，每次重启app都会重新访问链接加载这个源

另一种直接导入文件

