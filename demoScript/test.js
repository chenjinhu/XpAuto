function main() {
    home();
    sleep(1500);
    swipe(500,1500,500,300,500);
    sleep(500);
    swipe(500,1500,500,300,500);
    sleep(500);
   var selector = textMatch(".*Play.*")
    var result = click(selector);
    if (!result) {
        print("点击失败");
        return;
    }
    // click search view
    var searchBoxSel = idMatch(".*fake_search_box_top.*");
    sleep(1000)
    click(searchBoxSel);
    sleep(3000);
    var search = text("搜索");
    print(search)
    if(!search){
        click(search)
    }


}

main();
