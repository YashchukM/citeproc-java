$(document).ready(function() {
    //enable smooth scrolling
    //$('a').smoothScroll({ speed: 'auto' });
    
    var dontscroll = false;
    
    //enable smooth scrolling with back button support
    $(document).on('click', 'a[href*="#"]', function() {
      if (this.hash) {
        var tgt = this.hash.slice(1);
        $.bbq.pushState('#/' + tgt);
        if (document.getElementById(tgt)) {
          $.smoothScroll({scrollTarget: '#' + tgt});
        }
        return false;
      }
    });

    $(window).bind('hashchange', function(event) {
      var tgt = location.hash.replace(/^#\/?/, '');
      if (document.getElementById(tgt)) {
        if (dontscroll) {
          $.smoothScroll({scrollTarget: '#' + tgt, speed: 0});
        } else {
          $.smoothScroll({scrollTarget: '#' + tgt});
        }
        
        //after the first scroll (which happens after the page has been
        //loaded) enable scrolling
        dontscroll = false;
      }
    });

    console.log(window.location.hash);
    if (window.location.hash) {
      dontscroll = true;
      $(window).trigger('hashchange');
    }
    
    //set last accessed data in samples to today
    var date = new Date();
    var year = date.getFullYear();
    var month = date.getMonth() + 1;
    var day = date.getDay() + 1;
    var strday = "" + day;
    if (strday.length < 2) {
      strday = '0' + strday;
    }
    var monthnames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    var strmonth = monthnames[month - 1];
    $('.sample code').each(function() {
      var h = $(this).html();
      h = h.replace(/accessed\([0-9]+, [0-9]+, [0-9]+\)/, 'accessed(' +
        year + ', ' + month + ', ' + day + ')');
      h = h.replace(/\[Accessed\: [0-9]+-[a-zA-Z]+-[0-9]+\]/, '[Accessed: ' +
        strday + '-' + strmonth + '-' + year + ']');
      $(this).html(h);
    });
    
    $('.today-year').html(year);
    $('.today-month').html(strmonth);
    $('.today-day').html(strday);
});
