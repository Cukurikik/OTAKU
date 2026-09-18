                            val jsCheck = """
                                (function() {
                                    var title = document.title || '';
                                    if (title.indexOf('Just a moment') > -1 || title.indexOf('Cloudflare') > -1 || title.indexOf('Attention Required') > -1 || title.indexOf('Security check') > -1) {
                                        return 'CF';
                                    }
                                    var target = '$targetSelector';
                                    if (target !== '') {
                                        if (document.querySelector(target) != null) {
                                            return document.documentElement.outerHTML;
                                        } else {
                                            return 'WAIT';
                                        }
                                    }
                                    return document.documentElement.outerHTML;
                                })();
                            """.trimIndent()
